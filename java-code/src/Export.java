import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.util.List;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileNotFoundException;

public class Export {
	
	public Export() {
		// empty default constructor
	}

    /**
     * Creates a PDF document containing the simulation results in a table.
     * * @param fileName The name of the output file (e.g., "Report.pdf")
     * @param title    The title shown at the top of the document
     * @param data     The list of simulation entries to be tabulated
     */
    public void createPDF(String fileName, String title, List<String> data) {
        Document document = new Document(PageSize.A4);

        try {
            PdfWriter.getInstance(document, new FileOutputStream(fileName));
            document.open();

            // Add Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph p = new Paragraph(title, titleFont);
            p.setAlignment(Element.ALIGN_CENTER);
            p.setSpacingAfter(20);
            document.add(p);

            // Create Table (10 columns: Step,ID, Color, PosX, PosY, Edge, TL-ID, State, Index)
            PdfPTable table = new PdfPTable(10);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1, 2f, 2f, 2f, 1.5f, 1.5f, 1.5f, 2f, 2f, 2f});

            // Add Header Cells
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            String[] headers = {"Step", "Vehicle-ID", "Color", "Speed", "PosX", "PosY", "Edge",
                                "TrafficLight-ID", "Current State", "Current Index"};

            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            // Add Data Rows
            Font dataFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);            
            for (String row : data) {
                String[] parts = row.split(",", -1); // must split into 10 parts
                for(String part : parts){
                    PdfPCell cell = new PdfPCell(new Phrase(part, dataFont));
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            document.add(table);
//            System.out.println("PDF successfully created: " + fileName);
        } catch (Exception e) {
            System.err.println("Export Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Ensure document is closed to release file lock
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    public void createCSV(String fileName, List<String> data) {
        File cvsFile = new File(fileName);

        try (PrintWriter pw = new PrintWriter(cvsFile)){
        	// first line
            pw.println("Step,Vehicle-ID,Color,Speed,PosX,PosY,Edge,TrafficLight-ID,Current State,Current Index");

            for (String entry : data) {
                pw.println(entry);
            }
        } catch (FileNotFoundException e){
            System.err.println("Export Error: " + e.getMessage());
        }
    }
}































