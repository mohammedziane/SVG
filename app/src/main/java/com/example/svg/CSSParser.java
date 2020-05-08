package com.example.svg;

import android.util.Log;

import com.example.svg.SVGParser.TextScanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*

un parser CSS compatible SVG

*/
class CSSParser
{
    private static final String  TAG = "CSSParser";
    static final String  CSS_MIME_TYPE = "text/css";
    private static final String  ID = "id";
    private static final String  CLASS = "class";

    private static final int SPECIFICITY_ID_ATTRIBUTE             = 1000000;
    private static final int SPECIFICITY_ATTRIBUTE_OR_PSEUDOCLASS = 1000;
    private static final int SPECIFICITY_ELEMENT_OR_PSEUDOELEMENT = 1;

    private MediaType  deviceMediaType = null;
    private Source     source = null;    // Where these rules came from (Parser or RenderOptions)

    private boolean  inMediaRule = false;


    @SuppressWarnings("unused")
    enum MediaType
    {
        all,
        aural,       // deprecated
        braille,     // deprecated
        embossed,    // deprecated
        handheld,    // deprecated
        print,
        projection,  // deprecated
        screen,
        speech,
        tty,         // deprecated
        tv           // deprecated
    }

    private enum Combinator
    {
        DESCENDANT,  // E F
        CHILD,       // E > F
        FOLLOWS      // E + F
    }

    private enum AttribOp
    {
        EXISTS,     // *[foo]
        EQUALS,     // *[foo=bar]
        INCLUDES,   // *[foo~=bar]
        DASHMATCH,  // *[foo|=bar]
    }

    // Supported SVG attributes
    private enum  PseudoClassIdents
    {
        target,
        root,
        nth_child,
        nth_last_child,
        nth_of_type,
        nth_last_of_type,
        first_child,
        last_child,
        first_of_type,
        last_of_type,
        only_child,
        only_of_type,
        empty,
        not,

        // Others from  Selectors 3 (and earlier)
        // Supported but always fail to match.
        lang,  // might support later
        link, visited, hover, active, focus, enabled, disabled, checked, indeterminate,

        UNSUPPORTED;

        private static final Map<String, PseudoClassIdents> cache = new HashMap<>();

        static {
            for (PseudoClassIdents attr : values()) {
                if (attr != UNSUPPORTED) {
                    final String key = attr.name().replace('_', '-');
                    cache.put(key, attr);
                }
            }
        }

        public static PseudoClassIdents fromString(String str)
        {
            PseudoClassIdents attr = cache.get(str);
            if (attr != null) {
                return attr;
            }
            return UNSUPPORTED;
        }
    }


    private static class Attrib
    {
        final public String    name;
        final        AttribOp  operation;
        final public String    value;

        Attrib(String name, AttribOp op, String value)
        {
            this.name = name;
            this.operation = op;
            this.value = value;
        }
    }

    private static class SimpleSelector
    {
        Combinator         combinator = null;
        String             tag = null;       // null means "*"
        List<Attrib>       attribs = null;
        List<PseudoClass>  pseudos = null;

        SimpleSelector(Combinator combinator, String tag)
        {
            this.combinator = (combinator != null) ? combinator : Combinator.DESCENDANT;
            this.tag = tag;
        }

        void  addAttrib(String attrName, AttribOp op, String attrValue)
        {
            if (attribs == null)
                attribs = new ArrayList<>();
            attribs.add(new Attrib(attrName, op, attrValue));
        }

        void  addPseudo(PseudoClass pseudo)
        {
            if (pseudos == null)
                pseudos = new ArrayList<>();
            pseudos.add(pseudo);
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            if (combinator == Combinator.CHILD)
                sb.append("> ");
            else if (combinator == Combinator.FOLLOWS)
                sb.append("+ ");
            sb.append((tag == null) ? "*" : tag);
            if (attribs != null) {
                for (Attrib attr: attribs) {
                    sb.append('[').append(attr.name);
                    switch(attr.operation) {
                        case EQUALS: sb.append('=').append(attr.value); break;
                        case INCLUDES: sb.append("~=").append(attr.value); break;
                        case DASHMATCH: sb.append("|=").append(attr.value); break;
                        default: break;
                    }
                    sb.append(']');
                }
            }
            if (pseudos != null) {
                for (PseudoClass pseu: pseudos)
                    sb.append(':').append(pseu);
            }
            return sb.toString();
        }
    }

    static class  Ruleset
    {
        private List<Rule>  rules = null;

        // Add a rule to the ruleset. The position at which it is inserted is determined by its specificity value.
        void  add(Rule rule)
        {
            if (this.rules == null)
                this.rules = new ArrayList<>();
            for (int i = 0; i < rules.size(); i++)
            {
                Rule  nextRule = rules.get(i);
                if (nextRule.selector.specificity > rule.selector.specificity) {
                    rules.add(i, rule);
                    return;
                }
            }
            rules.add(rule);
        }

        void  addAll(Ruleset rules)
        {
            if (rules.rules == null)
                return;
            if (this.rules == null)
                this.rules = new ArrayList<>(rules.rules.size());
            for (Rule rule: rules.rules) {
                this.add(rule);
            }
        }

        List<Rule>  getRules()
        {
            return this.rules;
        }

        boolean  isEmpty()
        {
            return this.rules == null || this.rules.isEmpty();
        }

        int  ruleCount()
        {
            return (this.rules != null) ? this.rules.size() : 0;
        }

        /*
         * Remove all rules that were addres from a give Source.
         */
        void  removeFromSource(Source sourceToBeRemoved)
        {
            if (this.rules == null)
                return;
            Iterator<Rule> iter = this.rules.iterator();
            while (iter.hasNext()) {
                if (iter.next().source == sourceToBeRemoved)
                    iter.remove();
            }
        }

        @Override
        public String toString()
        {
            if (rules == null)
                return "";
            StringBuilder sb = new StringBuilder();
            for (Rule rule: rules)
                sb.append(rule.toString()).append('\n');
            return sb.toString();
        }
    }


    static enum  Source
    {
        Document,
        RenderOptions
    }


    static class  Rule
    {
        Selector   selector = null;
        SVG.Style  style = null;
        Source     source;

        Rule(Selector selector, SVG.Style style, Source source)
        {
            this.selector = selector;
            this.style = style;
            this.source = source;
        }

        @Override
        public String toString()
        {
            return String.valueOf(selector) + " {...} (src="+this.source+")";
        }
    }


    private static class Selector
    {
        List<SimpleSelector>  simpleSelectors = null;
        int                   specificity = 0;

        void  add(SimpleSelector part)
        {
            if (this.simpleSelectors == null)
                this.simpleSelectors = new ArrayList<>();
            this.simpleSelectors.add(part);
        }

        int size()
        {
            return (this.simpleSelectors == null) ? 0 : this.simpleSelectors.size();
        }

        SimpleSelector get(int i)
        {
            return this.simpleSelectors.get(i);
        }

        boolean isEmpty()
        {
            return (this.simpleSelectors == null) || this.simpleSelectors.isEmpty();
        }
      void  addedIdAttribute()
        {
            specificity += SPECIFICITY_ID_ATTRIBUTE;
        }

        void  addedAttributeOrPseudo()
        {
            specificity += SPECIFICITY_ATTRIBUTE_OR_PSEUDOCLASS;
        }

        void  addedElement()
        {
            specificity += SPECIFICITY_ELEMENT_OR_PSEUDOELEMENT;
        }

        @Override
        public String toString()
        {
            StringBuilder  sb = new StringBuilder();
            for (SimpleSelector sel: simpleSelectors)
                sb.append(sel).append(' ');
            return sb.append('[').append(specificity).append(']').toString();
        }
    }


    //===========================================================================================



    CSSParser()
    {
        this(MediaType.screen, Source.Document);
    }


    CSSParser(Source source)
    {
        this(MediaType.screen, source);
    }


    CSSParser(MediaType rendererMediaType, Source source)
    {
        this.deviceMediaType = rendererMediaType;
        this.source = source;
    }


    Ruleset  parse(String sheet)
    {
        CSSTextScanner  scan = new CSSTextScanner(sheet);
        scan.skipWhitespace();

        return parseRuleset(scan);
    }


    static boolean mediaMatches(String mediaListStr, MediaType rendererMediaType)
    {
        CSSTextScanner  scan = new CSSTextScanner(mediaListStr);
        scan.skipWhitespace();
        List<MediaType>  mediaList = parseMediaList(scan);
        return mediaMatches(mediaList, rendererMediaType);
    }


    //==============================================================================


    private static void  warn(String format, Object... args)
    {
        Log.w(TAG, String.format(format, args));
    }


    //==============================================================================


    static class CSSTextScanner extends TextScanner
    {
        CSSTextScanner(String input)
        {
            super(input.replaceAll("(?s)/\\*.*?\\*/", ""));  // strip all block comments
        }


        String  nextIdentifier()
        {
            int  end = scanForIdentifier();
            if (end == position)
                return null;
            String result = input.substring(position, end);
            position = end;
            return result;
        }

        private int  scanForIdentifier()
        {
            if (empty())
                return position;
            int  start = position;
            int  lastValidPos = position;

            int  ch = input.charAt(position);
            if (ch == '-')
                ch = advanceChar();
            // nmstart
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch == '_'))
            {
                ch = advanceChar();
                // nmchar
                while ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || (ch == '-') || (ch == '_')) {
                    ch = advanceChar();
                }
                lastValidPos = position;
            }
            position = start;
            return lastValidPos;
        }


        private List<Selector>  nextSelectorGroup() throws CSSParseException
        {
            if (empty())
                return null;

            ArrayList<Selector>  selectorGroup = new ArrayList<>(1);
            Selector             selector = new Selector();

            while (!empty())
            {
                if (nextSimpleSelector(selector))
                {

                    if (!skipCommaWhitespace())
                        continue;
                    selectorGroup.add(selector);
                    selector = new Selector();
                }
                else
                    break;
            }
            if (!selector.isEmpty())
                selectorGroup.add(selector);
            return selectorGroup;
        }


        boolean  nextSimpleSelector(Selector selector) throws CSSParseException
        {
            if (empty())
                return false;

            int             start = position;
            Combinator      combinator = null;
            SimpleSelector  selectorPart = null;

            if (!selector.isEmpty())
            {
                if (consume('>')) {
                    combinator = Combinator.CHILD;
                    skipWhitespace();
                } else if (consume('+')) {
                    combinator = Combinator.FOLLOWS;
                    skipWhitespace();
                }
            }

            if (consume('*')) {
                selectorPart = new SimpleSelector(combinator, null);
            } else {
                String tag = nextIdentifier();
                if (tag != null) {
                    selectorPart = new SimpleSelector(combinator, tag);
                    selector.addedElement();
                }
            }

            while (!empty())
            {
                if (consume('.'))
                {

                    if (selectorPart == null)
                        selectorPart = new SimpleSelector(combinator, null);
                    String  value = nextIdentifier();
                    if (value == null)
                        throw new CSSParseException("Invalid \".class\" simpleSelectors");
                    selectorPart.addAttrib(CLASS, AttribOp.EQUALS, value);
                    selector.addedAttributeOrPseudo();
                    continue;
                }

                if (consume('#'))
                {

                    if (selectorPart == null)
                        selectorPart = new SimpleSelector(combinator, null);
                    String  value = nextIdentifier();
                    if (value == null)
                        throw new CSSParseException("Invalid \"#id\" simpleSelectors");
                    selectorPart.addAttrib(ID, AttribOp.EQUALS, value);
                    selector.addedIdAttribute();
                    continue;
                }

                if (consume('['))
                {
                    if (selectorPart == null)
                        selectorPart = new SimpleSelector(combinator, null);
                    skipWhitespace();
                    String  attrName = nextIdentifier();
                    String  attrValue = null;
                    if (attrName == null)
                        throw new CSSParseException("Invalid attribute simpleSelectors");
                    skipWhitespace();
                    AttribOp  op = null;
                    if (consume('='))
                        op = AttribOp.EQUALS;
                    else if (consume("~="))
                        op = AttribOp.INCLUDES;
                    else if (consume("|="))
                        op = AttribOp.DASHMATCH;
                    if (op != null) {
                        skipWhitespace();
                        attrValue = nextAttribValue();
                        if (attrValue == null)
                            throw new CSSParseException("Invalid attribute simpleSelectors");
                        skipWhitespace();
                    }
                    if (!consume(']'))
                        throw new CSSParseException("Invalid attribute simpleSelectors");
                    selectorPart.addAttrib(attrName, (op == null) ? AttribOp.EXISTS : op, attrValue);
                    selector.addedAttributeOrPseudo();
                    continue;
                }

                if (consume(':'))
                {
                    if (selectorPart == null)
                        selectorPart = new SimpleSelector(combinator, null);
                    parsePseudoClass(selector, selectorPart);
                    continue;
                }

                break;
            }

            if (selectorPart != null)
            {
                selector.add(selectorPart);
                return true;
            }

            position = start;
            return false;
        }


        private static class  AnPlusB
        {
            public int a;
            public int b;

            AnPlusB(int a, int b) {
                this.a = a;
                this.b = b;
            }
        }


        private AnPlusB  nextAnPlusB() throws CSSParseException
        {
            if (empty())
                return null;

            int  start = position;

            if (!consume('('))
                return null;
            skipWhitespace();

            AnPlusB  result;
            if (consume("odd"))
                result = new AnPlusB(2, 1);
            else if (consume("even"))
                result = new AnPlusB(2, 0);
            else
            {

                int  aSign = 1,
                        bSign = 1;
                if (consume('+')) {
                    // do nothing
                } else if (consume('-')) {
                    bSign = -1;
                }
                // Then an integer
                IntegerParser  a = null,
                        b = IntegerParser.parseInt(input, position, inputLength, false);
                if (b != null)
                    position = b.getEndPos();
                // If an 'n' is next then that last part was the 'a' part. Now check for the 'b' part.
                if (consume('n') || consume('N')) {
                    a = (b != null) ? b : new IntegerParser(1, position);
                    aSign = bSign;
                    b = null;
                    bSign = 1;
                    skipWhitespace();
                    // Check for the sign for the b part
                    boolean  hasB = consume('+');
                    if (!hasB) {
                        hasB = consume('-');
                        if (hasB)
                            bSign = -1;
                    }
                    // If there was a sign, then the b integer should follow next
                    if (hasB) {
                        skipWhitespace();
                        b = IntegerParser.parseInt(input, position, inputLength, false);
                        if (b != null) {
                            position = b.getEndPos();
                        } else {
                            position = start;
                            return null;
                        }
                    }
                }
                // Construct the result in anticipation that we will get the end bracket next
                result = new AnPlusB((a == null) ? 0 : aSign * a.value(),
                        (b == null) ? 0 : bSign * b.value());
            }

            skipWhitespace();
            if (consume(')'))
                return result;

            position = start;
            return null;
        }


        private List<String>  nextIdentListParam() throws CSSParseException
        {
            if (empty())
                return null;

            int                start = position;
            ArrayList<String>  result = null;

            if (!consume('('))
                return null;
            skipWhitespace();

            while (true) {
                String  ident = nextIdentifier();
                if (ident == null) {
                    position = start;
                    return null;
                }
                if (result == null)
                    result = new ArrayList<>();
                result.add(ident);
                skipWhitespace();
                if (!skipCommaWhitespace())
                    break;
            }

            if (consume(')'))
                return result;

            position = start;
            return null;
        }


        private List<Selector>  nextPseudoNotParam() throws CSSParseException
        {
            if (empty())
                return null;

            int  start = position;

            if (!consume('('))
                return null;
            skipWhitespace();

            List<Selector>  result = nextSelectorGroup();

            if (result == null) {
                position = start;
                return null;
            }

            if (!consume(')')) {
                position = start;
                return null;
            }

            for (Selector selector: result) {
                if (selector.simpleSelectors == null)
                    break;
                for (SimpleSelector simpleSelector: selector.simpleSelectors) {
                    if (simpleSelector.pseudos == null)
                        break;
                    for (PseudoClass pseudo: simpleSelector.pseudos) {
                        if (pseudo instanceof PseudoClassNot)
                            return null;
                    }
                }
            }

            return result;
        }


        private void  parsePseudoClass(Selector selector, SimpleSelector selectorPart) throws CSSParseException
        {

            String  ident = nextIdentifier();
            if (ident == null)
                throw new CSSParseException("Invalid pseudo class");

            PseudoClass        pseudo = null;
            PseudoClassIdents  identEnum = PseudoClassIdents.fromString(ident);
            switch (identEnum)
            {
                case first_child:
                    pseudo = new PseudoClassAnPlusB(0, 1, true, false, null);
                    selector.addedAttributeOrPseudo();
                    break;

                case last_child:
                    pseudo = new PseudoClassAnPlusB(0, 1, false, false, null);
                    selector.addedAttributeOrPseudo();
                    break;

                case only_child:
                    pseudo = new PseudoClassOnlyChild(false, null);
                    selector.addedAttributeOrPseudo();
                    break;

                case first_of_type:
                    pseudo = new PseudoClassAnPlusB(0, 1, true, true, selectorPart.tag);
                    selector.addedAttributeOrPseudo();
                    break;

                case last_of_type:
                    pseudo = new PseudoClassAnPlusB(0, 1, false, true, selectorPart.tag);
                    selector.addedAttributeOrPseudo();
                    break;

                case only_of_type:
                    pseudo = new PseudoClassOnlyChild(true, selectorPart.tag);
                    selector.addedAttributeOrPseudo();
                    break;

                case root:
                    pseudo = new PseudoClassRoot();
                    selector.addedAttributeOrPseudo();
                    break;

                case empty:
                    pseudo = new PseudoClassEmpty();
                    selector.addedAttributeOrPseudo();
                    break;

                case nth_child:
                case nth_last_child:
                case nth_of_type:
                case nth_last_of_type:
                    boolean fromStart = identEnum == PseudoClassIdents.nth_child || identEnum == PseudoClassIdents.nth_of_type;
                    boolean ofType    = identEnum == PseudoClassIdents.nth_of_type || identEnum == PseudoClassIdents.nth_last_of_type;
                    AnPlusB  ab = nextAnPlusB();
                    if (ab == null)
                        throw new CSSParseException("Invalid or missing parameter section for pseudo class: " + ident);
                    pseudo = new PseudoClassAnPlusB(ab.a, ab.b, fromStart, ofType, selectorPart.tag);
                    selector.addedAttributeOrPseudo();
                    break;

                case not:
                    List<Selector>  notSelectorGroup = nextPseudoNotParam();
                    if (notSelectorGroup == null)
                        throw new CSSParseException("Invalid or missing parameter section for pseudo class: " + ident);
                    pseudo = new PseudoClassNot(notSelectorGroup);
                    selector.specificity = ((PseudoClassNot) pseudo).getSpecificity();
                    break;

                case target:
                    //TODO
                    pseudo = new PseudoClassTarget();
                    selector.addedAttributeOrPseudo();
                    break;

                case lang:
                    List<String>  langs = nextIdentListParam();
                    pseudo = new PseudoClassNotSupported(ident);
                    selector.addedAttributeOrPseudo();
                    break;

                case link:
                case visited:
                case hover:
                case active:
                case focus:
                case enabled:
                case disabled:
                case checked:
                case indeterminate:
                    pseudo = new PseudoClassNotSupported(ident);
                    selector.addedAttributeOrPseudo();
                    break;

                default:
                    throw new CSSParseException("Unsupported pseudo class: " + ident);
            }

            selectorPart.addPseudo(pseudo);
        }


        private String  nextAttribValue()
        {
            if (empty())
                return null;

            String  result = nextQuotedString();
            if (result != null)
                return result;
            return nextIdentifier();
        }


        String  nextPropertyValue()
        {
            if (empty())
                return null;
            int  start = position;
            int  lastValidPos = position;

            int  ch = input.charAt(position);
            while (ch != -1 && ch != ';' && ch != '}' && ch != '!' && !isEOL(ch)) {
                if (!isWhitespace(ch))
                    lastValidPos = position + 1;
                ch = advanceChar();
            }
            if (position > start)
                return input.substring(start, lastValidPos);
            position = start;
            return null;
        }

        String  nextCSSString()
        {
            if (empty())
                return null;
            int  ch = input.charAt(position);
            int  endQuote = ch;
            if (ch != '\'' && ch != '"')
                return null;

            StringBuilder  sb = new StringBuilder();
            position++;
            ch = nextChar();
            while (ch != -1 && ch != endQuote)
            {
                if (ch == '\\') {

                    ch = nextChar();
                    if (ch == -1)
                        continue;
                    if (ch == '\n' || ch == '\r' || ch == '\f') {
                        ch = nextChar();
                        continue;
                    }
                    int  hc = hexChar(ch);
                    if (hc != -1) {
                        int  codepoint = hc;
                        for (int i=1; i<=5; i++) {
                            ch = nextChar();
                            hc = hexChar(ch);
                            if (hc == -1)
                                break;
                            codepoint = codepoint * 16 + hc;
                        }
                        sb.append((char) codepoint);
                        continue;
                    }
                }
                sb.append((char) ch);
                ch = nextChar();
            }
            return sb.toString();
        }


        private int  hexChar(int ch)
        {
            if (ch >= '0' && ch <= '9')
                return ((int)ch - (int)'0');
            if (ch >= 'A' && ch <= 'F')
                return ((int)ch - (int)'A') + 10;
            if (ch >= 'a' && ch <= 'f')
                return ((int)ch - (int)'a') + 10;
            return -1;
        }



        String  nextURL()
        {
            if (empty())
                return null;
            int  start = position;
            if (!consume("url("))
                return null;

            skipWhitespace();

            String url = nextCSSString();
            if (url == null)
                url = nextLegacyURL();

            if (url == null) {
                position = start;
                return null;
            }

            skipWhitespace();

            if (empty() || consume(")"))
                return url;

            position = start;
            return null;
        }



        String  nextLegacyURL()
        {
            StringBuilder  sb = new StringBuilder();

            while (!empty())
            {
                int  ch = input.charAt(position);

                if (ch == '\'' || ch == '"' || ch == '(' || ch == ')' || isWhitespace(ch) || Character.isISOControl(ch))
                    break;

                position++;
                if (ch == '\\')
                {
                    if (empty())
                        continue;

                    ch = input.charAt(position++);
                    if (ch == '\n' || ch == '\r' || ch == '\f') {
                        continue;
                    }
                    int  hc = hexChar(ch);
                    if (hc != -1) {
                        int  codepoint = hc;
                        for (int i=1; i<=5; i++) {
                            if (empty())
                                break;
                            hc = hexChar( input.charAt(position) );
                            if (hc == -1)
                                break;
                            position++;
                            codepoint = codepoint * 16 + hc;
                        }
                        sb.append((char) codepoint);
                        continue;
                    }

                }
                sb.append((char) ch);
            }
            if (sb.length() == 0)
                return null;
            return sb.toString();
        }
    }


    //==============================================================================



    private static boolean mediaMatches(List<MediaType> mediaList, MediaType rendererMediaType)
    {
        for (MediaType type: mediaList) {
            if (type == MediaType.all || type == rendererMediaType)
                return true;
        }
        return false;
    }


    private static List<MediaType> parseMediaList(CSSTextScanner scan)
    {
        ArrayList<MediaType>  typeList = new ArrayList<>();
        while (!scan.empty()) {
            String  type = scan.nextWord();
            if (type == null)
                break;
            try {
                typeList.add(MediaType.valueOf(type));
            } catch (IllegalArgumentException e) {
                // Ignore invalid media types
            }

            if (!scan.skipCommaWhitespace())
                break;
        }
        return typeList;
    }


    private void  parseAtRule(Ruleset ruleset, CSSTextScanner scan) throws CSSParseException
    {
        String  atKeyword = scan.nextIdentifier();
        scan.skipWhitespace();
        if (atKeyword == null)
            throw new CSSParseException("Invalid '@' rule");
        if (!inMediaRule && atKeyword.equals("media"))
        {
            List<MediaType>  mediaList = parseMediaList(scan);
            if (!scan.consume('{'))
                throw new CSSParseException("Invalid @media rule: missing rule set");

            scan.skipWhitespace();
            if (mediaMatches(mediaList, deviceMediaType)) {
                inMediaRule = true;
                ruleset.addAll( parseRuleset(scan) );
                inMediaRule = false;
            } else {
                parseRuleset(scan);
            }

            if (!scan.empty() && !scan.consume('}'))
                throw new CSSParseException("Invalid @media rule: expected '}' at end of rule set");

        }
        else if (!inMediaRule && atKeyword.equals("import"))
        {
            String  file = scan.nextURL();
            if (file == null)
                file = scan.nextCSSString();
            if (file == null)
                throw new CSSParseException("Invalid @import rule: expected string or url()");

            scan.skipWhitespace();
            List<MediaType>  mediaList = parseMediaList(scan);

            if (!scan.empty() && !scan.consume(';'))
                throw new CSSParseException("Invalid @media rule: expected '}' at end of rule set");

            if (SVG.getFileResolver() != null && mediaMatches(mediaList, deviceMediaType)) {
                String  css = SVG.getFileResolver().resolveCSSStyleSheet(file);
                if (css == null)
                    return;
                ruleset.addAll( parse(css) );
            }
        }
        //} else if (atKeyword.equals("charset")) {
        else
        {
            // Unknown/unsupported at-rule
            warn("Ignoring @%s rule", atKeyword);
            skipAtRule(scan);
        }
        scan.skipWhitespace();
    }

    private void  skipAtRule(CSSTextScanner scan)
    {
        int depth = 0;
        while (!scan.empty())
        {
            int ch = scan.nextChar();
            if (ch == ';' && depth == 0)
                return;
            if (ch == '{')
                depth++;
            else if (ch == '}' && depth > 0) {
                if (--depth == 0)
                    return;
            }
        }
    }


    private Ruleset  parseRuleset(CSSTextScanner scan)
    {
        Ruleset  ruleset = new Ruleset();
        try
        {
            while (!scan.empty())
            {
                if (scan.consume("<!--"))
                    continue;
                if (scan.consume("-->"))
                    continue;

                if (scan.consume('@')) {
                    parseAtRule(ruleset, scan);
                    continue;
                }
                if (parseRule(ruleset, scan))
                    continue;

                break;
            }
        }
        catch (CSSParseException e)
        {
            Log.e(TAG, "CSS parser terminated early due to error: " + e.getMessage());
            if (LibConfig.DEBUG)
                Log.e(TAG,"Stacktrace:", e);
        }
        return ruleset;
    }


    private boolean  parseRule(Ruleset ruleset, CSSTextScanner scan) throws CSSParseException
    {
        List<Selector>  selectors = scan.nextSelectorGroup();
        if (selectors != null && !selectors.isEmpty())
        {
            if (!scan.consume('{'))
                throw new CSSParseException("Malformed rule block: expected '{'");
            scan.skipWhitespace();
            SVG.Style  ruleStyle = parseDeclarations(scan);
            scan.skipWhitespace();
            for (Selector selector: selectors) {
                ruleset.add( new Rule(selector, ruleStyle, source) );
            }
            return true;
        }
        else
        {
            return false;
        }
    }


    private SVG.Style  parseDeclarations(CSSTextScanner scan) throws CSSParseException
    {
        SVG.Style  ruleStyle = new SVG.Style();
        while (true)
        {
            String  propertyName = scan.nextIdentifier();
            scan.skipWhitespace();
            if (!scan.consume(':'))
                throw new CSSParseException("Expected ':'");
            scan.skipWhitespace();
            String  propertyValue = scan.nextPropertyValue();
            if (propertyValue == null)
                throw new CSSParseException("Expected property value");
            // Check for !important flag.
            scan.skipWhitespace();
            if (scan.consume('!')) {
                scan.skipWhitespace();
                if (!scan.consume("important")) {
                    throw new CSSParseException("Malformed rule set: found unexpected '!'");
                }
                // We don't do anything with these. We just ignore them. TODO
                scan.skipWhitespace();
            }
            scan.consume(';');
            // TODO: support CSS only values such as "inherit"
            SVGParser.processStyleProperty(ruleStyle, propertyName, propertyValue);
            scan.skipWhitespace();
            if (scan.empty() || scan.consume('}'))
                break;
        }
        return ruleStyle;
    }



    public static List<String>  parseClassAttribute(String val)
    {
        CSSTextScanner  scan = new CSSTextScanner(val);
        List<String>    classNameList = null;

        while (!scan.empty())
        {
            String  className = scan.nextToken();
            if (className == null)
                continue;
            if (classNameList == null)
                classNameList = new ArrayList<>();
            classNameList.add(className);
            scan.skipWhitespace();
        }
        return classNameList;
    }


    //==============================================================================


    static class RuleMatchContext
    {
        SVG.SvgElementBase targetElement;    // From RenderOptions.target() and used for the :target selector

        @Override
        public String toString()
        {
            if (targetElement != null)
                return String.format("<%s id=\"%s\">", targetElement.getNodeName(), targetElement.id);
            else
                return "";
        }
    }


    static boolean  ruleMatch(RuleMatchContext ruleMatchContext, Selector selector, SVG.SvgElementBase obj)
    {
        // Build the list of ancestor objects
        List<SVG.SvgContainer> ancestors = new ArrayList<>();
        SVG.SvgContainer parent = obj.parent;
        while (parent != null) {
            ancestors.add(0, parent);
            parent = ((SVG.SvgObject) parent).parent;
        }

        int  ancestorsPos = ancestors.size() - 1;


        if (selector.size() == 1)
            return selectorMatch(ruleMatchContext, selector.get(0), ancestors, ancestorsPos, obj);


        return ruleMatch(ruleMatchContext, selector, selector.size() - 1, ancestors, ancestorsPos, obj);
    }


    private static boolean  ruleMatch(RuleMatchContext ruleMatchContext, Selector selector, int selPartPos, List<SVG.SvgContainer> ancestors, int ancestorsPos, SVG.SvgElementBase obj)
    {

        SimpleSelector  sel = selector.get(selPartPos);
        if (!selectorMatch(ruleMatchContext, sel, ancestors, ancestorsPos, obj))
            return false;

        if (sel.combinator == Combinator.DESCENDANT)
        {
            if (selPartPos == 0)
                return true;
            // Search up the ancestors list for a node that matches the next simpleSelectors
            while (ancestorsPos >= 0) {
                if (ruleMatchOnAncestors(ruleMatchContext, selector, selPartPos - 1, ancestors, ancestorsPos))
                    return true;
                ancestorsPos--;
            }
            return false;
        }
        else if (sel.combinator == Combinator.CHILD)
        {
            return ruleMatchOnAncestors(ruleMatchContext, selector, selPartPos - 1, ancestors, ancestorsPos);
        }
        else //if (sel.combinator == Combinator.FOLLOWS)
        {
            int  childPos = getChildPosition(ancestors, ancestorsPos, obj);
            if (childPos <= 0)
                return false;
            SVG.SvgElementBase prevSibling = (SVG.SvgElementBase) obj.parent.getChildren().get(childPos - 1);
            return ruleMatch(ruleMatchContext, selector, selPartPos - 1, ancestors, ancestorsPos, prevSibling);
        }
    }


    private static boolean  ruleMatchOnAncestors(RuleMatchContext ruleMatchContext, Selector selector, int selPartPos, List<SVG.SvgContainer> ancestors, int ancestorsPos)
    {
        SimpleSelector  sel = selector.get(selPartPos);
        SVG.SvgElementBase obj = (SVG.SvgElementBase) ancestors.get(ancestorsPos);

        if (!selectorMatch(ruleMatchContext, sel, ancestors, ancestorsPos, obj))
            return false;


        if (sel.combinator == Combinator.DESCENDANT)
        {
            if (selPartPos == 0)
                return true;

            while (ancestorsPos > 0) {
                if (ruleMatchOnAncestors(ruleMatchContext, selector, selPartPos - 1, ancestors, --ancestorsPos))
                    return true;
            }
            return false;
        }
        else if (sel.combinator == Combinator.CHILD)
        {
            return ruleMatchOnAncestors(ruleMatchContext, selector, selPartPos - 1, ancestors, ancestorsPos - 1);
        }
        else
        {
            int  childPos = getChildPosition(ancestors, ancestorsPos, obj);
            if (childPos <= 0)
                return false;
            SVG.SvgElementBase prevSibling = (SVG.SvgElementBase) obj.parent.getChildren().get(childPos - 1);
            return ruleMatch(ruleMatchContext, selector, selPartPos - 1, ancestors, ancestorsPos, prevSibling);
        }
    }


    private static int getChildPosition(List<SVG.SvgContainer> ancestors, int ancestorsPos, SVG.SvgElementBase obj)
    {
        if (ancestorsPos < 0)
            return 0;
        if (ancestors.get(ancestorsPos) != obj.parent)
            return -1;
        int  childPos = 0;
        for (SVG.SvgObject child: obj.parent.getChildren())
        {
            if (child == obj)
                return childPos;
            childPos++;
        }
        return -1;
    }


    private static boolean selectorMatch(RuleMatchContext ruleMatchContext, SimpleSelector sel, List<SVG.SvgContainer> ancestors, int ancestorsPos, SVG.SvgElementBase obj)
    {

        if (sel.tag != null && !sel.tag.equals(obj.getNodeName().toLowerCase(Locale.US)))
            return false;


        if (sel.attribs != null)
        {
            for (Attrib attr: sel.attribs)
            {
                switch (attr.name) {
                    case ID:
                        if (!attr.value.equals(obj.id))
                            return false;
                        break;
                    case CLASS:
                        if (obj.classNames == null)
                            return false;
                        if (!obj.classNames.contains(attr.value))
                            return false;
                        break;
                    default:

                        return false;
                }
            }
        }


        if (sel.pseudos != null) {
            for (PseudoClass pseudo: sel.pseudos) {
                if (!pseudo.matches(ruleMatchContext, obj))
                    return false;
            }
        }


        return true;
    }


    //==============================================================================


    private static interface  PseudoClass
    {
        public boolean  matches(RuleMatchContext ruleMatchContext, SVG.SvgElementBase obj);
    }


    private static class  PseudoClassAnPlusB  implements PseudoClass
    {
        private int      a;
        private int      b;
        private boolean  isFromStart;
        private boolean  isOfType;
        private String   nodeName;

        PseudoClassAnPlusB(int a, int b, boolean isFromStart, boolean isOfType, String nodeName)
        {
            this.a = a;
            this.b = b;
            this.isFromStart = isFromStart;
            this.isOfType = isOfType;
            this.nodeName = nodeName;
        }

        @Override
        public boolean matches(RuleMatchContext ruleMatchContext, SVG.SvgElementBase obj)
        {
            String  nodeNameToCheck = (isOfType && nodeName == null) ? obj.getNodeName() : nodeName;


            int childPos = 0;
            int childCount = 1;

            if (obj.parent != null) {
                childCount = 0;
                for (SVG.SvgObject node: obj.parent.getChildren()) {
                    SVG.SvgElementBase child = (SVG.SvgElementBase) node;
                    if (child == obj)
                        childPos = childCount;
                    if (nodeNameToCheck == null || child.getNodeName().equals(nodeNameToCheck))
                        childCount++;
                }
            }

            childPos = isFromStart ? childPos + 1
                    : childCount - childPos;


            if (a == 0) {

                return childPos == b;
            }
            return ((childPos - b) % a) == 0 &&

                    (Integer.signum(childPos - b) == 0 || Integer.signum(childPos - b) == Integer.signum(a));
        }

        @Override
        public String toString()
        {
            String last = isFromStart ? "" : "last-";
            return isOfType ? String.format("nth-%schild(%dn%+d of type <%s>)", last, a, b, nodeName)
                    : String.format("nth-%schild(%dn%+d)", last, a, b);
        }

    }


    private static class  PseudoClassOnlyChild  implements PseudoClass
    {
        private boolean  isOfType;
        private String   nodeName;  // The node name for when isOfType is true


        public PseudoClassOnlyChild(boolean isOfType, String nodeName)
        {
            this.isOfType = isOfType;
            this.nodeName = nodeName;
        }

        @Override
        public boolean matches(RuleMatchContext ruleMatchContext, SVG.SvgElementBase obj)
        {

            String  nodeNameToCheck = (isOfType && nodeName == null) ? obj.getNodeName() : nodeName;


            int childCount = 1;

            if (obj.parent != null) {
                childCount = 0;
                for (SVG.SvgObject node: obj.parent.getChildren()) {
                    SVG.SvgElementBase child = (SVG.SvgElementBase) node;  // This should be safe. We shouldn't be styling any SvgObject that isn't an element.
                    if (nodeNameToCheck == null || child.getNodeName().equals(nodeNameToCheck))
                        childCount++;   // this is a child of the right type
                }
            }

            return (childCount == 1);
        }

        @Override
        public String toString()
        {
            return isOfType ? String.format("only-of-type <%s>", nodeName)
                    : String.format("only-child");
        }

    }


    private static class  PseudoClassRoot  implements PseudoClass
    {
        @Override
        public boolean matches(RuleMatchContext ruleMatchContext, SVG.SvgElementBase obj)
        {
            return obj.parent == null;
        }

        @Override
        public String toString()
        {
            return "root";
        }

    }


    private static class  PseudoClassEmpty  implements PseudoClass
    {
        @Override
        public boolean matches(RuleMatchContext ruleMatchContext, SVG.SvgElementBase obj)
        {

            if (obj instanceof SVG.SvgContainer)
                return ((SVG.SvgContainer)obj).getChildren().size() == 0;
            else
                return true;

        }

        @Override
        public String toString()
        {
            return "empty";
        }

    }


    private static class  PseudoClassNot  implements PseudoClass
    {
        private List<Selector>  selectorGroup;

        PseudoClassNot(List<Selector> selectorGroup)
        {
            this.selectorGroup = selectorGroup;
        }

        @Override
        public boolean matches(RuleMatchContext ruleMatchContext, SVG.SvgElementBase obj)
        {

            for (Selector selector: selectorGroup) {
                if (ruleMatch(ruleMatchContext, selector, obj))
                    return false;
            }
            return true;
        }

        int getSpecificity()
        {

            int highest = Integer.MIN_VALUE;
            for (Selector selector: selectorGroup) {
                if (selector.specificity > highest)
                    highest = selector.specificity;
            }
            return highest;
        }

        @Override
        public String toString()
        {
            return "not(" + selectorGroup + ")";
        }

    }


    private static class  PseudoClassTarget  implements PseudoClass
    {
        @Override
        public boolean matches(RuleMatchContext ruleMatchContext, SVG.SvgElementBase obj)
        {
            if (ruleMatchContext != null)
                return obj == ruleMatchContext.targetElement;
            else
                return false;
        }

        @Override
        public String toString()
        {
            return "target";
        }

    }

    private static class  PseudoClassNotSupported  implements PseudoClass{
        private String  clazz;

        PseudoClassNotSupported(String clazz)
        {
            this.clazz = clazz;
        }

        @Override
        public boolean matches(RuleMatchContext ruleMatchContext, SVG.SvgElementBase obj)
        {
            return false;
        }

        @Override
        public String toString()
        {
            return clazz;
        }


    }
}
