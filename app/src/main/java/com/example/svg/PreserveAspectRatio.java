package com.example.svg;


/** <preserveAspectRatio> -->SVG
 *exemple:
 * <svg version="1.1" viewBox="0 0 200 100">
*/
public class PreserveAspectRatio
{
    private Alignment  alignment;
    private Scale      scale;

  //natural
    public static final PreserveAspectRatio  UNSCALED = new PreserveAspectRatio(null, null);

  //<preserveAspectRatio="none">
   public static final PreserveAspectRatio  STRETCH = new PreserveAspectRatio(Alignment.none, null);

    //<preserveAspectRatio="xMidYMid meet">
    public static final PreserveAspectRatio  LETTERBOX = new PreserveAspectRatio(Alignment.xMidYMid, Scale.meet);

    //<preserveAspectRatio="xMinYMin meet">-->SVG.
    public static final PreserveAspectRatio  START = new PreserveAspectRatio(Alignment.xMinYMin, Scale.meet);

   // <preserveAspectRatio="xMaxYMax meet">
   public static final PreserveAspectRatio  END = new PreserveAspectRatio(Alignment.xMaxYMax, Scale.meet);

    // <preserveAspectRatio="xMidYMin meet">
    public static final PreserveAspectRatio  TOP = new PreserveAspectRatio(Alignment.xMidYMin, Scale.meet);

    // <preserveAspectRatio="xMidYMax meet">
    public static final PreserveAspectRatio  BOTTOM = new PreserveAspectRatio(Alignment.xMidYMax, Scale.meet);

    // <preserveAspectRatio="xMidYMid slice">
   public static final PreserveAspectRatio  FULLSCREEN = new PreserveAspectRatio(Alignment.xMidYMid, Scale.slice);

     //<preserveAspectRatio="xMinYMin slice">
    public static final PreserveAspectRatio  FULLSCREEN_START = new PreserveAspectRatio(Alignment.xMinYMin, Scale.slice);


    public enum Alignment
    {
        /** Document is stretched to fit both the width and height of the viewport. When using this Alignment value, the value of Scale is not used and will be ignored. */
        none,
        /** Document is positioned at the top left of the viewport. */
        xMinYMin,
        /** Document is positioned at the centre top of the viewport. */
        xMidYMin,
        /** Document is positioned at the top right of the viewport. */
        xMaxYMin,
        /** Document is positioned at the middle left of the viewport. */
        xMinYMid,
        /** Document is centred in the viewport both vertically and horizontally. */
        xMidYMid,
        /** Document is positioned at the middle right of the viewport. */
        xMaxYMid,
        /** Document is positioned at the bottom left of the viewport. */
        xMinYMax,
        /** Document is positioned at the bottom centre of the viewport. */
        xMidYMax,
        /** Document is positioned at the bottom right of the viewport. */
        xMaxYMax
    }


    public enum Scale
    {
        /**
         * The document is scaled so that it is as large as possible without overflowing the viewport.
         * There may be blank areas on one or more sides of the document.
         */
        meet,
        /**
         * The document is scaled so that entirely fills the viewport. That means that some of the
         * document may fall outside the viewport and will not be rendered.
         */
        slice
    }

    PreserveAspectRatio(Alignment alignment, Scale scale)
    {
        this.alignment = alignment;
        this.scale = scale;
    }

    public static PreserveAspectRatio  of(String value)
    {
        try {
            return SVGParser.parsePreserveAspectRatio(value);
        } catch (SVGParseException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }


    /**
     * Returns the alignment value of this instance.
     * @return the alignment
     */
    @SuppressWarnings("WeakerAccess")
    public Alignment  getAlignment()
    {
        return alignment;
    }


    /**
     * Returns the scale value of this instance.
     * @return the scale
     */
    @SuppressWarnings("WeakerAccess")
    public Scale  getScale()
    {
        return scale;
    }


    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PreserveAspectRatio other = (PreserveAspectRatio) obj;
        return (alignment == other.alignment && scale == other.scale);
    }


    @Override
    public String toString()
    {
        return alignment + " " + scale;
    }
}
