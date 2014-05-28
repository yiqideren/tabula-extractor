package org.nerdpower.tabula.extractors;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nerdpower.tabula.Cell;
import org.nerdpower.tabula.Page;
import org.nerdpower.tabula.Rectangle;
import org.nerdpower.tabula.Ruling;
import org.nerdpower.tabula.Table;
import org.nerdpower.tabula.TableWithRulingLines;
import org.nerdpower.tabula.TextElement;

public class SpreadsheetExtractionAlgorithm implements ExtractionAlgorithm {
    
    private static final float ARBITRARY_MAGIC_HEURISTIC_NUMBER = 0.65f;
    
    private static final Comparator<Point2D> POINT_COMPARATOR = new Comparator<Point2D>() {
        @Override
        public int compare(Point2D arg0, Point2D arg1) {
            int rv = 0;
            if (arg0.getY() > arg1.getY()) {
                rv = 1;
            }
            else if (arg0.getY() < arg1.getY()) {
                rv = -1;
            }
            else if (arg0.getX() > arg1.getX()) {
                rv = 1;
            }
            else if (arg0.getX() < arg1.getX()) {
                rv = -1;
            }
            return rv;
        }
    };
    
    private static final Comparator<Point2D> X_FIRST_POINT_COMPARATOR = new Comparator<Point2D>() {
        @Override
        public int compare(Point2D arg0, Point2D arg1) {
            int rv = 0;
            if (arg0.getX() > arg1.getX()) {
                rv = 1;
            }
            else if (arg0.getX() < arg1.getX()) {
                rv = -1;
            }
            else if (arg0.getY() > arg1.getY()) {
                rv = 1;
            }
            else if (arg0.getY() < arg1.getY()) {
                rv = -1;
            }
            return rv;
        }
    };

    @Override
    public List<? extends Table> extract(Page page) {
        // TODO Auto-generated method stub
        List<Cell> cells = findCells(page.getHorizontalRulings(), page.getVerticalRulings());
        List<Rectangle> spreadsheetAreas = findSpreadsheetsFromCells(cells);
        
        List<TableWithRulingLines> spreadsheets = new ArrayList<TableWithRulingLines>();
        for (Rectangle area: spreadsheetAreas) {

            List<Cell> overlappingCells = new ArrayList<Cell>();
            for (Cell c: cells) {
                if (c.intersects(area)) {
                    c.setTextElements(TextElement.mergeWords(page.getText(c)));
                    overlappingCells.add(c);
                }
            }

            List<Ruling> horizontalOverlappingRulings = new ArrayList<Ruling>();
            for (Ruling hr: page.getHorizontalRulings()) {
                if (area.intersectsLine(hr)) {
                    horizontalOverlappingRulings.add(hr);
                }
            }
            List<Ruling> verticalOverlappingRulings = new ArrayList<Ruling>();
            for (Ruling vr: page.getHorizontalRulings()) {
                if (area.intersectsLine(vr)) {
                    verticalOverlappingRulings.add(vr);
                }
            }
            
            spreadsheets.add(new TableWithRulingLines(area, page, overlappingCells,
                    horizontalOverlappingRulings, verticalOverlappingRulings));
        }
        
        return spreadsheets;
    }
    
    public boolean isTabular(Page page) {
        List<? extends Table> tables = new SpreadsheetExtractionAlgorithm().extract(page);
        if (tables.size() == 0) {
            return false;
        }
        Table table = tables.get(0);
        int rowsDefinedByLines = table.getRows().size();
        int colsDefinedByLines = table.getCols().size();
        
        tables = new BasicExtractionAlgorithm().extract(page);
        if (tables.size() == 0) {
            // TODO WHAT DO WE DO HERE?
        }
        table = tables.get(0);
        int rowsDefinedWithoutLines = table.getRows().size();
        int colsDefinedWithoutLines = table.getCols().size();
        
        float ratio = (((float) colsDefinedByLines / colsDefinedWithoutLines) + ((float) rowsDefinedByLines / rowsDefinedWithoutLines)) / 2.0f;
        
        return ratio > ARBITRARY_MAGIC_HEURISTIC_NUMBER && ratio < (1/ARBITRARY_MAGIC_HEURISTIC_NUMBER);
    }
    
    public List<Cell> findCells(List<Ruling> horizontalRulingLines, List<Ruling> verticalRulingLines) {
        List<Cell> cellsFound = new ArrayList<Cell>();
        Map<Point2D, Ruling[]> intersectionPoints = Ruling.findIntersections(horizontalRulingLines, verticalRulingLines);
        List<Point2D> intersectionPointsList = new ArrayList<Point2D>(intersectionPoints.keySet());
        boolean doBreak = false;
        
        Collections.sort(intersectionPointsList, POINT_COMPARATOR); 
        
        
        for (int i = 0; i < intersectionPointsList.size(); i++) {
            Point2D topLeft = intersectionPointsList.get(i);
            Ruling[] hv = intersectionPoints.get(topLeft);
            doBreak = false;
            
            // CrossingPointsDirectlyBelow( topLeft );
            // CrossingPointsDirectlyToTheRight( topLeft );

            List<Point2D> xPoints = new ArrayList<Point2D>();
            List<Point2D> yPoints = new ArrayList<Point2D>();

                
            for (Point2D p: intersectionPointsList.subList(i, intersectionPointsList.size())) {
                if (p.getX() == topLeft.getX() && p.getY() > topLeft.getY()) {
                    xPoints.add(p);
                }
                if (p.getY() == topLeft.getY() && p.getX() > topLeft.getX()) {
                    yPoints.add(p);
                }
            }
            outer:
            for (Point2D xPoint: xPoints) {
                if (doBreak) { break; }

                if (!hv[1].colinear(xPoint)) {
                    continue;
                }
                for (Point2D yPoint: yPoints) {
                    if (!hv[0].colinear(yPoint)) {
                        continue;
                    }
                    Point2D btmRight = new Point2D.Float((float) yPoint.getX(), (float) xPoint.getY());
                    if (intersectionPoints.containsKey(btmRight)) {
                        Ruling[] btmRightHV = intersectionPoints.get(btmRight);
                        if (btmRightHV[0].colinear(xPoint) && btmRightHV[1].colinear(yPoint)) {
                            cellsFound.add(new Cell(topLeft, btmRight));
                        }
                        doBreak = true;
                        break outer;
                    }
                }
            }
        }
        
        // TODO create cells for vertical ruling lines with aligned endpoints at the top/bottom of a grid 
        // that aren't connected with an horizontal ruler?
        // see: https://github.com/jazzido/tabula-extractor/issues/78#issuecomment-41481207
        
        return cellsFound;
    }
    
    public List<Rectangle> findSpreadsheetsFromCells(List<? extends Rectangle> cells) {
        // via: http://stackoverflow.com/questions/13746284/merging-multiple-adjacent-rectangles-into-one-polygon
        List<Rectangle> rectangles = new ArrayList<Rectangle>();
        Set<Point2D> pointSet = new HashSet<Point2D>();
        Map<Point2D, Point2D> edgesH = new HashMap<Point2D, Point2D>();
        Map<Point2D, Point2D> edgesV = new HashMap<Point2D, Point2D>();
        int i = 0;
        
        cells = new ArrayList<Rectangle>(new HashSet<Rectangle>(cells));
        
        Collections.sort(cells);
        
        for (Rectangle cell: cells) {
            for(Point2D pt: cell.getPoints()) {
                if (pointSet.contains(pt)) { // shared vertex, remove it
                    pointSet.remove(pt);
                }
                else {
                    pointSet.add(pt);
                }
            }
        }
        
        // X first sort
        List<Point2D> pointsSortX = new ArrayList<Point2D>(pointSet);
        Collections.sort(pointsSortX, X_FIRST_POINT_COMPARATOR);
        // Y first sort
        List<Point2D> pointsSortY = new ArrayList<Point2D>(pointSet);
        Collections.sort(pointsSortY, POINT_COMPARATOR);
        
        while (i < pointSet.size()) {
            float currY = (float) pointsSortY.get(i).getY();
            while (i < pointSet.size() && pointsSortY.get(i).getY() == currY) {
                edgesH.put(pointsSortY.get(i), pointsSortY.get(i+1));
                edgesH.put(pointsSortY.get(i+1), pointsSortY.get(i));
                i += 2;
            }
        }
        
        i = 0;
        while (i < pointSet.size()) {
            float currX = (float) pointsSortX.get(i).getX();
            while (i < pointSet.size() && pointsSortX.get(i).getX() == currX) {
                edgesV.put(pointsSortX.get(i), pointsSortX.get(i+1));
                edgesV.put(pointsSortX.get(i+1), pointsSortX.get(i));
                i += 2;
            }
        }
        
        // Get all the polygons
        List<List<PolygonVertex>> polygons = new ArrayList<List<PolygonVertex>>();
        Point2D nextVertex;
        while (!edgesH.isEmpty()) {
            ArrayList<PolygonVertex> polygon = new ArrayList<PolygonVertex>();
            Point2D first = edgesH.keySet().iterator().next();
            polygon.add(new PolygonVertex(first, Direction.HORIZONTAL));
            edgesH.remove(first);
            
            while (true) {
                PolygonVertex curr = polygon.get(polygon.size() - 1);
                PolygonVertex lastAddedVertex;
                if (curr.direction == Direction.HORIZONTAL) {
                    nextVertex = edgesV.get(curr.point);
                    edgesV.remove(curr.point);
                    lastAddedVertex = new PolygonVertex(nextVertex, Direction.VERTICAL); 
                    polygon.add(lastAddedVertex);
                }
                else {
                    nextVertex = edgesH.get(curr.point);
                    edgesH.remove(curr.point);
                    lastAddedVertex = new PolygonVertex(nextVertex, Direction.HORIZONTAL);
                    polygon.add(lastAddedVertex);
                }
                
                if (lastAddedVertex.equals(polygon.get(0))) {
                    // closed polygon
                    polygon.remove(polygon.size() - 1);
                    break;
                }
            }
            
            for (PolygonVertex vertex: polygon) {
                edgesH.remove(vertex.point);
                edgesV.remove(vertex.point);
            }
            polygons.add(polygon);
        }
        
        // calculate grid-aligned minimum area rectangles for each found polygon
        for(List<PolygonVertex> poly: polygons) {
            float top = java.lang.Float.MAX_VALUE;
            float left = java.lang.Float.MAX_VALUE;
            float bottom = java.lang.Float.MIN_VALUE;
            float right = java.lang.Float.MIN_VALUE;
            for (PolygonVertex pt: poly) {
                top = (float) Math.min(top, pt.point.getY());
                left = (float) Math.min(left, pt.point.getX());
                bottom = (float) Math.max(bottom, pt.point.getY());
                right = (float) Math.max(right, pt.point.getX());
            }
            rectangles.add(new Rectangle(top, left, right - left, bottom - top));
        }
        
        return rectangles;
    }
    
    @Override
    public String toString() {
        return "spreadsheet";
    }
    
    private enum Direction {
        HORIZONTAL,
        VERTICAL
    }
    
     class PolygonVertex {
        Point2D point;
        Direction direction;
        
        public PolygonVertex(Point2D point, Direction direction) {
            this.direction = direction;
            this.point = point;
        }
        
        public boolean equals(PolygonVertex other) {
            return this.point.equals(other.point);
        }
        
        public int hashCode() {
            return this.point.hashCode();
        }
        
        public String toString() {
            return String.format("%s[point=%s,direction=%s]", this.getClass().getName(), this.point.toString(), this.direction.toString());
        }
    }
}
