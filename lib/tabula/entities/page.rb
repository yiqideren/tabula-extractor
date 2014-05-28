java_import org.nerdpower.tabula.Page
java_import org.nerdpower.tabula.extractors.BasicExtractionAlgorithm

class Page
  include Tabula::HasCells
  attr_accessor :file_path, :cells

  #returns a Table object
  def get_table(options={})
    options = {:vertical_rulings => []}.merge(options)

    tables = if options[:vertical_rulings].empty?
               BasicExtractionAlgorithm.new.extract(self)
             else
               BasicExtractionAlgorithm.new(options[:vertical_rulings]).extract(self)
             end

    tables.first

  end

  #for API backwards-compatibility reasons, this returns an array of arrays.
  def make_table(options={})
    get_table(options).rows
  end

  # returns the Spreadsheets; creating them if they're not memoized
  def spreadsheets(options={})
    unless @spreadsheets.nil?
      return @spreadsheets
    end

    tables = SpreadsheetExtractionAlgorithm.new.extract(self).sort

    # self.find_cells!(self.getHorizontalRulings, self.getVerticalRulings, options)

    # spreadsheet_areas = find_spreadsheets_from_cells #literally, java.awt.geom.Area objects. lol sorry. polygons.

    # #transform each spreadsheet area into a rectangle
    # # and get the cells contained within it.
    # spreadsheet_rectangle_areas = spreadsheet_areas.map{ |a| a.getBounds2D }

    # @spreadsheets = spreadsheet_rectangle_areas.map do |rect|
    #   spr = Tabula::Spreadsheet.new(rect.y, rect.x,
    #                         rect.width, rect.height,
    #                         self,
    #                         #TODO: keep track of the cells, instead of getting them again inefficiently.
    #                         [],
    #                         vertical_ruling_lines.select{|vl| rect.intersectsLine(vl) },
    #                         horizontal_ruling_lines.select{|hl| rect.intersectsLine(hl) }
    #                        )
    #   spr.cells = @cells.select{ |c| spr.intersects(c) }
    #   spr.add_spanning_cells!
    #   spr
    # end
    # if options[:fill_in_cells]
    #   fill_in_cells!
    # end
    # spreadsheets
  end

  def fill_in_cells!(options={})
    spreadsheets(options).each do |spreadsheet|
      spreadsheet.cells.each do |cell|
        cell.text_elements = page.get_cell_text(cell)
      end
      spreadsheet.cells_resolved = true
    end
  end

  def number(indexing_base=:one_indexed)
    if indexing_base == :zero_indexed
      return @number_one_indexed - 1
    else
      return @number_one_indexed
    end
  end

  # TODO no need for this, let's choose one name
  def ruling_lines
    get_ruling_lines!
  end

  def horizontal_ruling_lines
    self.getHorizontalRulings
  end

  def vertical_ruling_lines
    self.getVerticalRulings
  end

  #returns ruling lines, memoizes them in
  def get_ruling_lines!
    self.get_rulings
  end

  def get_cell_text(area=nil)
    TextElement.merge_words(self.get_text(area))
  end

  def to_json(options={})
    { :width => self.width,
      :height => self.height,
      :number => self.number,
      :rotation => self.rotation,
      :texts => self.text
    }.to_json(options)
  end
end

module Tabula
  Page = ::Page
end
