import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.util.List;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.scene.paint.Color;

public class Export {

    public Export() {
        // empty default constructor
    }

    /**
     * Creates a PDF document containing the simulation results in a table.
     * @param fileName The name of the output file (e.g., "Report.pdf")
     * @param title    The title shown at the top of the document
     * @param data     The list of simulation entries to be tabulated
     * @param vWrap
     * @param tWrap
     * @param eWrap
     */
    public void createPDF(String fileName, String title, List<String> data, UI ui) {
        Document document = new Document(PageSize.A4);

        try {
            PdfWriter.getInstance(document, new FileOutputStream(fileName));
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font tableTopFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font dataFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

            // Add Title
            Paragraph p = new Paragraph(title, titleFont);
            p.setAlignment(Element.ALIGN_CENTER);
            p.setSpacingAfter(20);
            document.add(p);

            // Add misc data: date, time, general stats
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String nowStr = now.format(format);				// timestamp DONE
            int sumVehicles = ui.getVehWrapper().getVehicleCount();		// total vehicles DONE
            int sumTLS = ui.getTrafWrapper().getTrafficLightCount();	// total traffic lights DONE
            int sumEdges = ui.getEdgeWrapper().getEdgeCount();			// total edges DONE
            double sumWaitTime = 0;							// total waiting time of all cars up till now DONE
            int sumLanes = 0;								// total lanes DONE
            int sumHaltingVehs = 0;							// total congested vehicles DONE
//            String slowestEdge = "";						// name of most congested edge DONE
//            int slowestEdgeVehCount = 0;					// number of congested vehicles on the most congested edge DONE
//            String fastestEdge = "";						// name of the fastest edge DONE
//            double fastestEdgeSpeed = 0;					// avg speed on the fastest edge DONE
//            int numBusstops = ui.getInfWrapper().getBusStopIds().size();	// number of bus stops
            double avgVehPerEdge = ui.getEdgeWrapper().getAvgVehiclesPerEdge();	// avg numVehPerEdge DONE

            System.out.println("Doing edge loop"); // takes very long
            List<String> eIDs = ui.getEdgeWrapper().getEdgeIDs();
            int eIDsize = eIDs.size();
            int ii = 1;
            double quo = 0;
            for (String eID : eIDs) {
                if ((double)ii / eIDsize >= quo) {
                    System.out.printf("\tcompleted %.1f%%\n", quo * 100);
                    quo += 0.1;
                }
                sumLanes += ui.getEdgeWrapper().getLaneNumberOfEdge(eID);
                ii++;

                if (ui.getEdgeWrapper().getNumVehicle(eID) == 0) continue; // skip empty edges to speed up
                sumWaitTime += ui.getEdgeWrapper().getWaitingTimeSum(eID);
                int ca = ui.getEdgeWrapper().getLastStepHaltingNumber(eID);
                sumHaltingVehs += ca;
//            	if (ca > slowestEdgeVehCount) {
//            		slowestEdge = eID;
//            		slowestEdgeVehCount = ca;
//            	}
//            	double cad = ui.getEdgeWrapper().getLastStepMeanSpeed(eID);
//            	if (cad > fastestEdgeSpeed) {
//            		fastestEdge = eID;
//            		fastestEdgeSpeed = cad;
//            	}
            }
            String fastestVeh = "";			// DONE
            double fastestVehSpeed = 0;		// DONE
            String slowestVeh = "";			// DONE
            double slowestVehSpeed = 1000; // so that it catches the slowest one instead of being 0 all the time	DONE
            double avgVehSpeed = 0;			// DONE
            System.out.println("Doing vehicle loop");
            for (String vID : ui.getVehWrapper().getVehicleIds()) {
                avgVehSpeed += ui.getVehWrapper().getSpeed(vID);
                if (ui.getVehWrapper().getSpeed(vID) > fastestVehSpeed) {
                    fastestVeh = vID;
                    fastestVehSpeed = ui.getVehWrapper().getSpeed(vID);
                }
                if (ui.getVehWrapper().getSpeed(vID) < slowestVehSpeed) {
                    slowestVeh = vID;
                    slowestVehSpeed = ui.getVehWrapper().getSpeed(vID);
                }
            }
            avgVehSpeed = avgVehSpeed / sumVehicles;

            // create table for metrics
            p = new Paragraph("Statistics and Metrics", headerFont);
            p.setAlignment(Element.ALIGN_LEFT);
            p.setSpacingBefore(20);
            p.setSpacingAfter(10);
            document.add(p);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(60);
            table.setWidths(new float[]{2f, 1f});
            table.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(new PdfPCell(new Phrase("Timestamp", dataFont)));
            table.addCell(new PdfPCell(new Phrase(nowStr, dataFont)));
            table.addCell(new PdfPCell(new Phrase("Total edges", dataFont)));
            table.addCell(new PdfPCell(new Phrase(sumEdges + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Total lanes", dataFont)));
            table.addCell(new PdfPCell(new Phrase(sumLanes + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Total traffic lights", dataFont)));
            table.addCell(new PdfPCell(new Phrase(sumTLS + "", dataFont)));
////            tab.addCell(new PdfPCell(new Phrase("Total bus stops", dataFont)));
////            tab.addCell(new PdfPCell(new Phrase(numBusstops + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Total vehicles", dataFont)));
            table.addCell(new PdfPCell(new Phrase(sumVehicles + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Total congested vehicles", dataFont)));
            table.addCell(new PdfPCell(new Phrase(sumHaltingVehs + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Total wait time", dataFont)));
            table.addCell(new PdfPCell(new Phrase(sumWaitTime + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Average vehicles per edge", dataFont)));
            table.addCell(new PdfPCell(new Phrase(avgVehPerEdge + "", dataFont)));
//            table.addCell(new PdfPCell(new Phrase("Fastest edge", dataFont)));
//            table.addCell(new PdfPCell(new Phrase(fastestEdge, dataFont)));
//            table.addCell(new PdfPCell(new Phrase("Average speed of fastest edge", dataFont)));
//            table.addCell(new PdfPCell(new Phrase(fastestEdgeSpeed + "", dataFont)));
//            table.addCell(new PdfPCell(new Phrase("Slowest edge", dataFont)));
//            table.addCell(new PdfPCell(new Phrase(slowestEdge, dataFont)));
//            table.addCell(new PdfPCell(new Phrase("Slowest edge congested vehicle count", dataFont)));
//            table.addCell(new PdfPCell(new Phrase(slowestEdgeVehCount + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Fastest vehicle", dataFont)));
            table.addCell(new PdfPCell(new Phrase(fastestVeh, dataFont)));
            table.addCell(new PdfPCell(new Phrase("Fastest vehicle speed", dataFont)));
            table.addCell(new PdfPCell(new Phrase(fastestVehSpeed + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Slowest vehicle", dataFont)));
            table.addCell(new PdfPCell(new Phrase(slowestVeh, dataFont)));
            table.addCell(new PdfPCell(new Phrase("Slowest vehicle speed", dataFont)));
            table.addCell(new PdfPCell(new Phrase(slowestVehSpeed + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Average vehicle speed", dataFont)));
            table.addCell(new PdfPCell(new Phrase(avgVehSpeed + "", dataFont)));
            document.add(table);

            // Add all data of TLs
            p = new Paragraph("All Traffic Light Data", headerFont);
            p.setAlignment(Element.ALIGN_LEFT);
            p.setSpacingBefore(20);
            p.setSpacingAfter(10);
            document.add(p);
            // Create Table for traffic lights (4 columns:)
            table = new PdfPTable(4);
            table.setWidthPercentage(60);
            table.setWidths(new float[]{1, 1.5f, 3f, 1.5f});
            table.setHorizontalAlignment(Element.ALIGN_LEFT);
            // Add Header Cells
            String[] headers = new String[] {"Step", "TrafficLight-ID", "Current State", "Current Phase Index"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, tableTopFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            // Add Data Rows
            for (String row : data) {
                String[] parts = row.split(",", -1); // must split into 10 parts
                if (parts[7] == "") break; // blank data
                for(int i = 0; i < 10; i++){
                    switch (i) {
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                        case 6:
                            continue; // skip vehicle data
                        case 0:
                        case 7:
                        case 8:
                        case 9:
                            PdfPCell cell = new PdfPCell(new Phrase(parts[i], dataFont));
                            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                            table.addCell(cell);
                            break;
                        default:
                    }
                }
            }
            document.add(table);

            // Add all data of vehicles
            p = new Paragraph("All Vehicle Data", headerFont);
            p.setAlignment(Element.ALIGN_LEFT);
            p.setSpacingBefore(20);
            p.setSpacingAfter(10);
            document.add(p);
            // Create Table for all vehicle and traffic lights (7 columns:)
            table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1, 3f, 2f, 1.5f, 1.5f, 1.5f, 1.5f});
            table.setHorizontalAlignment(Element.ALIGN_LEFT);
            // Add Header Cells
            headers = new String[] {"Step", "Vehicle-ID", "Color [R-G-B-A]", "Speed [m/s]",
                "PosX", "PosY", "Edge"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, tableTopFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            // Add Data Rows
            for (String row : data) {
                String[] parts = row.split(",", -1); // must split into 10 parts
                if (parts[1] == "") break; // blank data
                for(int i = 0; i < 7; i++){
                    PdfPCell cell = new PdfPCell(new Phrase(parts[i], dataFont));
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            document.add(table);

            // Filter: red cars only
            p = new Paragraph("Filter: Red Cars Only", headerFont);
            p.setAlignment(Element.ALIGN_LEFT);
            p.setSpacingBefore(20);
            p.setSpacingAfter(10);
            document.add(p);

            table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1, 3f, 2f, 1.5f, 1.5f, 1.5f, 1.5f});
            table.setHorizontalAlignment(Element.ALIGN_LEFT);
            // Add Header Cells
            headers = new String[] {"Step", "Vehicle-ID", "Color [R-G-B-A]", "Speed [m/s]",
                "PosX", "PosY", "Edge"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, tableTopFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            // Add Data Rows
            for (String row : data) {
                String[] parts = row.split(",", -1); // must split into 10 parts
                if (parts[1] == "") break; // blank data
                String[] color = parts[2].split("-"); // 4 elements
                Color actualc = Color.rgb(Integer.valueOf(color[0]),
                                        Integer.valueOf(color[1]),
                                        Integer.valueOf(color[2]),
                                        Double.valueOf(color[3]) / 255);
                Color target = Color.rgb(255, 0, 0, 1.0);
                if (UI.isSimilarColor(actualc, target, 0.18)) {
                    // only use the first 7
                    for(int i = 0; i < 7; i++) {
                        PdfPCell cell = new PdfPCell(new Phrase(parts[i], dataFont));
                        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        table.addCell(cell);
                    }
                }
            }
            document.add(table);

            // Filter: congested vehicles only
            p = new Paragraph("Filter: Congested Vehicles Only", headerFont);
            p.setAlignment(Element.ALIGN_LEFT);
            p.setSpacingBefore(20);
            p.setSpacingAfter(10);
            document.add(p);

            table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1, 3f, 2f, 1.5f, 1.5f, 1.5f, 1.5f});
            table.setHorizontalAlignment(Element.ALIGN_LEFT);
            // Add Header Cells
            headers = new String[] {"Step", "Vehicle-ID", "Color [R-G-B-A]", "Speed [m/s]",
                "PosX", "PosY", "Edge"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, tableTopFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            // Add Data Rows
            for (String row : data) {
                String[] parts = row.split(",", -1); // must split into 10 parts
                if (parts[1] == "") break; // blank data

                if (Double.valueOf(parts[3]) <= 0.5) {
                    // only use the first 7
                    for(int i = 0; i < 7; i++) {
                        PdfPCell cell = new PdfPCell(new Phrase(parts[i], dataFont));
                        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        table.addCell(cell);
                    }
                }
            }
            document.add(table);
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
            pw.println("Step,Vehicle-ID,Color [R-G-B-A],Speed [m/s],PosX,PosY,Edge,TrafficLight-ID,Current State,Phase Index");

            for (String entry : data) {
                pw.println(entry);
            }
        } catch (FileNotFoundException e){
            System.err.println("Export Error: " + e.getMessage());
        }
    }
}
