package org.nerdpower.tabula;

import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.font.PDFont;

@SuppressWarnings("serial")
public class TextElement extends Rectangle {

    private final String text;
    private final PDFont font;
    private float fontSize;
    private float widthOfSpace, dir;

    public TextElement(float y, float x, float width, float height,
            PDFont font, float fontSize, String c, float widthOfSpace) {
        super();
        this.setRect(x, y, width, height);
        this.text = c;
        this.widthOfSpace = widthOfSpace;
        this.fontSize = fontSize;
        this.font = font;
    } 

    public TextElement(float y, float x, float width, float height,
            PDFont font, float fontSize, String c, float widthOfSpace, float dir) {
        super();
        this.setRect(x, y, width, height);
        this.text = c;
        this.widthOfSpace = widthOfSpace;
        this.fontSize = fontSize;
        this.font = font;
        this.dir = dir;
    }

    public String getText() {
        return text;
    }

    public float getDirection() {
        return dir;
    }

    public float getWidthOfSpace() {
        return widthOfSpace;
    }

    public PDFont getFont() {
        return font;
    }

    public float getFontSize() {
        return fontSize;
    }
    
    public static List<TextChunk> mergeWords(List<TextElement> textElements) {
        return mergeWords(textElements, new ArrayList<Ruling>());
    }
    
    /**
     * heuristically merge a list of TextElement into a list of TextChunk
     * ported from from PDFBox's PDFTextStripper.writePage, with modifications.
     * Here be dragons
     * 
     * @param textElements
     * @param verticalRulings
     * @return
     */
    public static List<TextChunk> mergeWords(List<TextElement> textElements, List<Ruling> verticalRulings) {
        
        List<TextChunk> textChunks = new ArrayList<TextChunk>();
        
        if (textElements.isEmpty()) {
            return textChunks;
        }
        
        textChunks.add(new TextChunk(textElements.remove(0)));
        TextChunk firstTC = textChunks.get(0); 
        
        float previousAveCharWidth = (float) firstTC.getWidth();
        float endOfLastTextX = (float) firstTC.getRight();
        float maxYForLine = (float) firstTC.getBottom();
        float maxHeightForLine = (float) firstTC.getHeight();
        float minYTopForLine = (float) firstTC.getTop();
        float lastWordSpacing = -1;
        float wordSpacing, deltaSpace, averageCharWidth, deltaCharWidth;
        float expectedStartOfNextWordX, dist;
        TextElement sp, prevChar;
        TextChunk currentChunk;
        boolean sameLine, acrossVerticalRuling;
        
        for (TextElement chr : textElements) {
            currentChunk = textChunks.get(textChunks.size() - 1);
            prevChar = currentChunk.textElements.get(currentChunk.textElements.size() - 1);
            
            // if same char AND overlapped, skip
            if ((chr.getText().equals(prevChar.getText())) && (prevChar.overlapRatio(chr) > 0.5)) {
                continue;
            }
            
            // if chr is a space that overlaps with prevChar, skip
            if (chr.getText().equals(" ") && prevChar.getLeft() == chr.getLeft() && prevChar.getTop() == chr.getTop()) {
                continue;
            }
            
            // Resets the average character width when we see a change in font
            // or a change in the font size
            if ((chr.getFont() != prevChar.getFont()) || (chr.getFontSize() != prevChar.getFontSize())) {
                previousAveCharWidth = -1;
            }

            // is there any vertical ruling that goes across chr and prevChar?
            acrossVerticalRuling = false;
            for (Ruling r: verticalRulings) {
                if (prevChar.x < r.getLeft() && chr.x > r.getLeft()) {
                    acrossVerticalRuling = true;
                    break;
                }
            } 
            
            // Estimate the expected width of the space based on the
            // space character with some margin.
            wordSpacing = (float) chr.getWidthOfSpace();
            deltaSpace = 0;
            if (java.lang.Float.isNaN(wordSpacing) || wordSpacing == 0) {
                deltaSpace = java.lang.Float.MAX_VALUE;
            }
            else if (lastWordSpacing < 0) {
                deltaSpace = wordSpacing * 0.5f; // 0.5 == spacing tolerance
            }
            else {
                deltaSpace = ((wordSpacing + lastWordSpacing) / 2.0f) * 0.5f;
            }
            
            // Estimate the expected width of the space based on the
            // average character width with some margin. This calculation does not
            // make a true average (average of averages) but we found that it gave the
            // best results after numerous experiments. Based on experiments we also found that
            // .3 worked well.
            if (previousAveCharWidth < 0) {
                averageCharWidth = (float) (chr.getWidth() / chr.getText().length());
            }
            else {
                averageCharWidth = (float) ((previousAveCharWidth + (chr.getWidth() / chr.getText().length())) / 2.0f);
            }
            deltaCharWidth = averageCharWidth * 0.3f; // 0.3 == average char tolerance
            
            // Compares the values obtained by the average method and the wordSpacing method and picks
            // the smaller number.
            expectedStartOfNextWordX = -java.lang.Float.MAX_VALUE;
            
            if (endOfLastTextX != -1) {
                expectedStartOfNextWordX = endOfLastTextX + Math.min(deltaCharWidth, deltaSpace);
            }
            
            // new line?
            sameLine = true;
            if (!Utils.overlap((float) chr.getBottom(), chr.height, maxYForLine, maxHeightForLine)) {
                endOfLastTextX = -1;
                expectedStartOfNextWordX = -java.lang.Float.MAX_VALUE;
                maxYForLine = -java.lang.Float.MAX_VALUE;
                maxHeightForLine = -1;
                minYTopForLine = java.lang.Float.MAX_VALUE;
                sameLine = false;
            }
            
            endOfLastTextX = (float) chr.getRight();
            
            // should we add a space?
            if (!acrossVerticalRuling &&
                sameLine &&
                expectedStartOfNextWordX < chr.getLeft() && 
                !prevChar.getText().endsWith(" ")) {
                
                sp = new TextElement((float) prevChar.getTop(),
                        (float) prevChar.getLeft(),
                        (float) (expectedStartOfNextWordX - prevChar.getLeft()),
                        (float) prevChar.getHeight(),
                        prevChar.getFont(),
                        prevChar.getFontSize(),
                        " ",
                        prevChar.getWidthOfSpace());
                
                currentChunk.add(sp);
            }
            else {
                sp = null;
            }
            
            maxYForLine = (float) Math.max(chr.getBottom(), maxYForLine);
            maxHeightForLine = (float) Math.max(maxHeightForLine, chr.getHeight());
            minYTopForLine = (float) Math.min(minYTopForLine, chr.getTop());

            dist = (float) (chr.getLeft() - (sp != null ? sp.getRight() : prevChar.getRight()));

            if (!acrossVerticalRuling &&
                sameLine &&
                (dist < 0 ? currentChunk.verticallyOverlaps(chr) : dist < wordSpacing)) {
                currentChunk.add(chr);
            }
            else { // create a new chunk
               textChunks.add(new TextChunk(chr));
            }
            
            lastWordSpacing = wordSpacing;
            previousAveCharWidth = (float) (sp != null ? (averageCharWidth + sp.getWidth()) / 2.0f : averageCharWidth);
        }
        return textChunks;
    }
}
