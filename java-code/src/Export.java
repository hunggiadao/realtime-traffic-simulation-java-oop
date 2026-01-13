import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
            // PERF: writing a lot of PDF bytes is faster with buffering (same PDF output/quality).
            PdfWriter.getInstance(document, new BufferedOutputStream(new FileOutputStream(fileName)));
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

            // PERF: cache wrapper references (tiny win, but avoids repeated method chaining).
            VehicleWrapper vehWrapper = ui.getVehWrapper();
            TrafficLightWrapper trafWrapper = ui.getTrafWrapper();
            EdgeWrapper edgeWrapper = ui.getEdgeWrapper();

            int sumVehicles = vehWrapper.getVehicleCount();		// total vehicles DONE
            int sumTLS = trafWrapper.getTrafficLightCount();	// total traffic lights DONE
            int sumEdges = edgeWrapper.getEdgeCount();			// total edges DONE
            double sumWaitTime = 0;							// total waiting time of all cars up till now DONE
            int sumLanes = 0;								// total lanes DONE
            int sumHaltingVehs = 0;							// total congested vehicles DONE
//            String slowestEdge = "";						// name of most congested edge DONE
//            int slowestEdgeVehCount = 0;					// number of congested vehicles on the most congested edge DONE
//            String fastestEdge = "";						// name of the fastest edge DONE
//            double fastestEdgeSpeed = 0;					// avg speed on the fastest edge DONE
//            int numBusstops = ui.getInfWrapper().getBusStopIds().size();	// number of bus stops
            double avgVehPerEdge = edgeWrapper.getAvgVehiclesPerEdge();	// avg numVehPerEdge DONE

            // PERF NOTE:
            // - This loop used to feel slow mostly because of getNumVehicle(eID) (see below).
            // - We still iterate edges once, but we avoid any O(edges*vehicles) recomputation.
            List<String> eIDs = edgeWrapper.getEdgeIDs();
            int eIDsize = eIDs.size();
            int ii = 1;
            double quo = 0;
            for (String eID : eIDs) {
                if ((double)ii / eIDsize >= quo) {
                    System.out.printf("\tcompleted %.1f%%\n", quo * 100);
                    quo += 0.1;
                }
                sumLanes += edgeWrapper.getLaneNumberOfEdge(eID);
                ii++;

                // IMPORTANT PERF:
                // Do NOT call edgeWrapper.getNumVehicle(eID) here.
                // In EdgeWrapper it triggers updateEdgeData(), which loops ALL edges * ALL vehicles each time.
                // Using TraCI last-step vehicle count keeps this loop cheap.
                if (edgeWrapper.getLastStepVehicleNumber(eID) == 0) continue; // skip empty edges to speed up
                sumWaitTime += edgeWrapper.getWaitingTimeSum(eID);
                int ca = edgeWrapper.getLastStepHaltingNumber(eID);
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
            for (String vID : vehWrapper.getVehicleIds()) {
                // PERF: read speed once per vehicle (avoid repeated wrapper calls).
                double speed = vehWrapper.getSpeed(vID);
                avgVehSpeed += speed;
                if (speed > fastestVehSpeed) {
                    fastestVeh = vID;
                    fastestVehSpeed = speed;
                }
                if (speed < slowestVehSpeed) {
                    slowestVeh = vID;
                    slowestVehSpeed = speed;
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

            // --- Build all data tables in a single pass over 'data' ---
            // PERF (main win): previously we looped over 'data' multiple times and did row.split() each time.
            // Now each row is split once, and we fill all tables in one pass.

            // Traffic Light table (4 cols: step + TL fields)
            PdfPTable tlTable = new PdfPTable(4);
            tlTable.setWidthPercentage(60);
            tlTable.setWidths(new float[]{1, 1.5f, 3f, 1.5f});
            tlTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            String[] tlHeaders = new String[] {"Step", "TrafficLight-ID", "Current State", "Current Phase Index"};
            for (String header : tlHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(header, tableTopFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                tlTable.addCell(cell);
            }

            // Vehicle table + filter tables (7 cols)
            String[] vehHeaders = new String[] {"Step", "Vehicle-ID", "Color [R-G-B-A]", "Speed [m/s]", "PosX", "PosY", "Edge"};

            PdfPTable vehTable = new PdfPTable(7);
            vehTable.setWidthPercentage(100);
            vehTable.setWidths(new float[]{1, 3f, 2f, 1.5f, 1.5f, 1.5f, 1.5f});
            vehTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            for (String header : vehHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(header, tableTopFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                vehTable.addCell(cell);
            }

            PdfPTable redTable = new PdfPTable(7);
            redTable.setWidthPercentage(100);
            redTable.setWidths(new float[]{1, 3f, 2f, 1.5f, 1.5f, 1.5f, 1.5f});
            redTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            for (String header : vehHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(header, tableTopFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                redTable.addCell(cell);
            }

            PdfPTable congestedTable = new PdfPTable(7);
            congestedTable.setWidthPercentage(100);
            congestedTable.setWidths(new float[]{1, 3f, 2f, 1.5f, 1.5f, 1.5f, 1.5f});
            congestedTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            for (String header : vehHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(header, tableTopFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                congestedTable.addCell(cell);
            }

            boolean tlEnded = false;
            boolean vehEnded = false;
            boolean seenTl = false;
            boolean seenVeh = false;

            for (String row : data) {
                String[] parts = row.split(",", -1); // expected 10 parts
                if (parts.length < 10) continue;

                boolean hasVeh = !parts[1].isEmpty();
                boolean hasTl = !parts[7].isEmpty();

                if (hasTl && !tlEnded) {
                    seenTl = true;
                    addCenteredCell(tlTable, parts[0], dataFont);
                    addCenteredCell(tlTable, parts[7], dataFont);
                    addCenteredCell(tlTable, parts[8], dataFont);
                    addCenteredCell(tlTable, parts[9], dataFont);
                } else if (!hasTl && seenTl) {
                    tlEnded = true; // stop once padded tail begins
                }

                if (hasVeh && !vehEnded) {
                    seenVeh = true;
                    for (int i = 0; i < 7; i++) {
                        addCenteredCell(vehTable, parts[i], dataFont);
                    }

                    // Filter: Congested Vehicles Only (speed <= 0.5)
                    double speed;
                    try {
                        speed = Double.parseDouble(parts[3]);
                    } catch (NumberFormatException ignored) {
                        speed = Double.POSITIVE_INFINITY;
                    }
                    if (speed <= 0.5) {
                        for (int i = 0; i < 7; i++) {
                            addCenteredCell(congestedTable, parts[i], dataFont);
                        }
                    }

                    // Filter: Red Cars Only
                    // PERF: do the same tolerance check as UI.isSimilarColor, but without constructing javafx.scene.paint.Color
                    // for every single row (keeps output identical: same tolerance, same target red).
                    if (!parts[2].isEmpty()) {
                        String[] color = parts[2].split("-", -1);
                        if (color.length >= 3) {
                            try {
                                double r = Integer.parseInt(color[0]) / 255.0;
                                double g = Integer.parseInt(color[1]) / 255.0;
                                double b = Integer.parseInt(color[2]) / 255.0;
                                if (Math.abs(r - 1.0) <= 0.18 && Math.abs(g) <= 0.18 && Math.abs(b) <= 0.18) {
                                    for (int i = 0; i < 7; i++) {
                                        addCenteredCell(redTable, parts[i], dataFont);
                                    }
                                }
                            } catch (NumberFormatException ignored) {
                                // ignore invalid color rows
                            }
                        }
                    }
                } else if (!hasVeh && seenVeh) {
                    vehEnded = true; // stop once padded tail begins
                }

                if (tlEnded && vehEnded) {
                    break;
                }
            }

            // Add all data of TLs
            p = new Paragraph("All Traffic Light Data", headerFont);
            p.setAlignment(Element.ALIGN_LEFT);
            p.setSpacingBefore(20);
            p.setSpacingAfter(10);
            document.add(p);
            document.add(tlTable);

            // Add all data of vehicles
            p = new Paragraph("All Vehicle Data", headerFont);
            p.setAlignment(Element.ALIGN_LEFT);
            p.setSpacingBefore(20);
            p.setSpacingAfter(10);
            document.add(p);
            document.add(vehTable);

            // Filter: red cars only
            p = new Paragraph("Filter: Red Cars Only", headerFont);
            p.setAlignment(Element.ALIGN_LEFT);
            p.setSpacingBefore(20);
            p.setSpacingAfter(10);
            document.add(p);
            document.add(redTable);

            // Filter: congested vehicles only
            p = new Paragraph("Filter: Congested Vehicles Only", headerFont);
            p.setAlignment(Element.ALIGN_LEFT);
            p.setSpacingBefore(20);
            p.setSpacingAfter(10);
            document.add(p);
            document.add(congestedTable);
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

    // Small helper to keep the loops clean (no behavior change).
    private static void addCenteredCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
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
