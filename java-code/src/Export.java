import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;

import java.io.ByteArrayOutputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class Export {

    // Higher snapshot scale => sharper charts in PDF.
    private static final double EXPORT_SNAPSHOT_SCALE = 2.0;

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
            Font chartCaptionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

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
////        tab.addCell(new PdfPCell(new Phrase("Total bus stops", dataFont)));
////        tab.addCell(new PdfPCell(new Phrase(numBusstops + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Total vehicles", dataFont)));
            table.addCell(new PdfPCell(new Phrase(sumVehicles + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Total congested vehicles", dataFont)));
            table.addCell(new PdfPCell(new Phrase(sumHaltingVehs + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Total wait time", dataFont)));
            table.addCell(new PdfPCell(new Phrase(sumWaitTime + "", dataFont)));
            table.addCell(new PdfPCell(new Phrase("Average vehicles per edge", dataFont)));
            table.addCell(new PdfPCell(new Phrase(avgVehPerEdge + "", dataFont)));
//          table.addCell(new PdfPCell(new Phrase("Fastest edge", dataFont)));
//          table.addCell(new PdfPCell(new Phrase(fastestEdge, dataFont)));
//          table.addCell(new PdfPCell(new Phrase("Average speed of fastest edge", dataFont)));
//          table.addCell(new PdfPCell(new Phrase(fastestEdgeSpeed + "", dataFont)));
//          table.addCell(new PdfPCell(new Phrase("Slowest edge", dataFont)));
//          table.addCell(new PdfPCell(new Phrase(slowestEdge, dataFont)));
//          table.addCell(new PdfPCell(new Phrase("Slowest edge congested vehicle count", dataFont)));
//          table.addCell(new PdfPCell(new Phrase(slowestEdgeVehCount + "", dataFont)));
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

            // ---- Graphs page (4 charts in a 2x2 grid) ----
            // Put graphs at the end of the report.
            try {
                addGraphsPage(document, headerFont, chartCaptionFont, ui);
            } catch (Exception ignored) {
                // If snapshot fails (e.g., charts not in scene), still export the rest of the report.
            }
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

    private void addGraphsPage(Document document, Font headerFont, Font captionFont, UI ui) throws Exception {
        if (ui == null) return;
        Map<String, Node> graphs = ui.getExportGraphs();
        if (graphs == null || graphs.isEmpty()) return;

        // Do NOT force a page break; let iText flow naturally right after the last section.
        // If there isn't enough space, iText will automatically continue on the next page.
        document.add(Chunk.NEWLINE);

        Paragraph p = new Paragraph("Graphs", headerFont);
        p.setAlignment(Element.ALIGN_LEFT);
        p.setSpacingAfter(10);
        document.add(p);

        // 2 columns => 2x2 for 4 graphs
        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setHorizontalAlignment(Element.ALIGN_LEFT);
        grid.setWidths(new float[]{1f, 1f});

        Rectangle page = document.getPageSize();
        float usableW = page.getWidth() - document.leftMargin() - document.rightMargin();
        float cellW = (usableW - 8f) / 2f;
        float cellH = 260f;

        int added = 0;
        for (Map.Entry<String, Node> e : graphs.entrySet()) {
            if (added >= 4) break;
            String caption = e.getKey();
            Node node = e.getValue();
            if (node == null) continue;

            Image img;
            if (node instanceof PieChart) {
                // Snapshotting a PieChart directly can be empty when it's inside a non-selected Tab.
                // Render an offscreen PieChart from export data (no reparenting, no Swing).
                img = renderVehicleColorPieJavaFx(ui, 780, 520);
                if (img == null) img = snapshotNodeToPdfImage(node);
            } else img = snapshotNodeToPdfImage(node);
            if (img == null) continue;

            img.scaleToFit(cellW, cellH);
            img.setAlignment(Image.ALIGN_CENTER);

            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(6f);

            Paragraph cap = new Paragraph(caption, captionFont);
            cap.setAlignment(Element.ALIGN_CENTER);
            cap.setSpacingAfter(6f);

            cell.addElement(cap);
            cell.addElement(img);
            grid.addCell(cell);
            added++;
        }

        // If we ended up with an odd number of charts, add an empty cell to complete the row.
        if ((added % 2) == 1) {
            PdfPCell empty = new PdfPCell(new Phrase(""));
            empty.setBorder(Rectangle.NO_BORDER);
            empty.setPadding(6f);
            grid.addCell(empty);
        }

        if (added > 0) {
            document.add(grid);
        }
    }

    private Image renderVehicleColorPieJavaFx(UI ui, int width, int height) {
        try {
            if (ui == null) return null;
            List<UI.PieSliceExport> slices = ui.getVehicleColorPieExport();
            if (slices == null || slices.isEmpty()) return null;

            return callOnFxThread(() -> {
                // Build a standalone PieChart + legend (no reparenting of the live UI chart).
                ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
                for (UI.PieSliceExport s : slices) {
                    if (s == null || s.value <= 0.0) continue;
                    String label = (s.label == null) ? "" : s.label;
                    data.add(new PieChart.Data(label, s.value));
                }
                if (data.isEmpty()) return null;

                PieChart pie = new PieChart(data);
                pie.setLegendVisible(false); // we'll render our own compact legend
                pie.setLabelsVisible(false);
                pie.setAnimated(false);

                int pad = 16;
                int legendW = 240;

                pie.setPrefSize(Math.max(10, width - legendW - pad * 2), Math.max(10, height - pad * 2));
                pie.setMinSize(pie.getPrefWidth(), pie.getPrefHeight());
                pie.setMaxSize(pie.getPrefWidth(), pie.getPrefHeight());

                VBox legend = new VBox(10);
                legend.setPrefWidth(legendW);
                legend.setMinWidth(legendW);
                legend.setMaxWidth(legendW);
                legend.setAlignment(Pos.TOP_LEFT);

                double total = 0.0;
                for (UI.PieSliceExport s : slices) {
                    if (s != null && s.value > 0.0) total += s.value;
                }
                if (total <= 0.0) return null;

                // Create a root so CSS/skins initialize reliably.
                BorderPane root = new BorderPane();
                root.setPrefSize(width, height);
                root.setMinSize(width, height);
                root.setMaxSize(width, height);
                root.setPadding(new Insets(pad));
                root.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
                root.setCenter(pie);
                root.setRight(legend);
                BorderPane.setAlignment(pie, Pos.CENTER);
                BorderPane.setAlignment(legend, Pos.TOP_LEFT);

                // Attach to a Scene so JavaFX applies default chart CSS.
                new Scene(root, width, height, Color.WHITE);

                root.applyCss();
                root.layout();

                // Apply slice colors + build legend items.
                int i = 0;
                for (UI.PieSliceExport s : slices) {
                    if (s == null || s.value <= 0.0) continue;
                    if (i >= pie.getData().size()) break;

                    String cssColor = (s.cssColor == null) ? null : s.cssColor.trim();
                    Color fxColor;
                    try {
                        fxColor = (cssColor == null || cssColor.isEmpty()) ? Color.web("#9E9E9E") : Color.web(cssColor);
                    } catch (Exception ignored) {
                        fxColor = Color.web("#9E9E9E");
                    }

                    Node sliceNode = pie.getData().get(i).getNode();
                    if (sliceNode != null && cssColor != null && !cssColor.isEmpty()) {
                        sliceNode.setStyle("-fx-pie-color: " + cssColor + ";");
                    }

                    double pct = (s.value / total) * 100.0;
                    String label = (s.label == null) ? "" : s.label;

                    javafx.scene.shape.Rectangle swatch = new javafx.scene.shape.Rectangle(14, 14);
                    swatch.setFill(fxColor);
                    swatch.setStroke(Color.web("#E0E0E0"));

                    Label text = new Label(String.format("%s (%.0f%%)", label, pct));
                    text.setStyle("-fx-font-size: 14px;");
                    text.setTextFill(Color.web("#282828"));

                    HBox row = new HBox(10, swatch, text);
                    row.setAlignment(Pos.CENTER_LEFT);
                    legend.getChildren().add(row);
                    i++;
                }

                root.applyCss();
                root.layout();

                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.WHITE);
                params.setTransform(Transform.scale(EXPORT_SNAPSHOT_SCALE, EXPORT_SNAPSHOT_SCALE));
                WritableImage fxImg = new WritableImage(
                        (int) Math.ceil(width * EXPORT_SNAPSHOT_SCALE),
                        (int) Math.ceil(height * EXPORT_SNAPSHOT_SCALE)
                );
                WritableImage out = root.snapshot(params, fxImg);
                if (out == null) return null;
                return Image.getInstance(encodePng(out));
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private Image snapshotNodeToPdfImage(Node node) {
        try {
            WritableImage fxImg = callOnFxThread(() -> {
                // Ensure CSS/layout are applied so charts render correctly in snapshots.
                node.applyCss();
                if (node instanceof Parent) ((Parent) node).layout();

                double w = Math.max(1.0, node.getLayoutBounds().getWidth());
                double h = Math.max(1.0, node.getLayoutBounds().getHeight());

                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.WHITE);
                params.setTransform(Transform.scale(EXPORT_SNAPSHOT_SCALE, EXPORT_SNAPSHOT_SCALE));

                WritableImage target = new WritableImage(
                        (int) Math.ceil(w * EXPORT_SNAPSHOT_SCALE),
                        (int) Math.ceil(h * EXPORT_SNAPSHOT_SCALE)
                );
                return node.snapshot(params, target);
            });
            if (fxImg == null) return null;
            return Image.getInstance(encodePng(fxImg));
        } catch (Exception e) {
            return null;
        }
    }

    private static <T> T callOnFxThread(Callable<T> callable) throws Exception {
        if (Platform.isFxApplicationThread()) return callable.call();
        FutureTask<T> task = new FutureTask<>(callable);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    // Minimal PNG encoder (RGBA, 8-bit, no interlace). Avoids Swing/AWT dependencies.
    private static byte[] encodePng(WritableImage img) throws Exception {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        if (w <= 0 || h <= 0 || img.getPixelReader() == null) return null;

        // Raw image bytes: each row starts with filter byte 0, then RGBA pixels.
        int rowLen = 1 + w * 4;
        byte[] raw = new byte[rowLen * h];
        int p = 0;
        for (int y = 0; y < h; y++) {
            raw[p++] = 0; // filter type 0
            for (int x = 0; x < w; x++) {
                int argb = img.getPixelReader().getArgb(x, y);
                raw[p++] = (byte) ((argb >> 16) & 0xFF); // R
                raw[p++] = (byte) ((argb >> 8) & 0xFF);  // G
                raw[p++] = (byte) (argb & 0xFF);         // B
                raw[p++] = (byte) ((argb >>> 24) & 0xFF);// A
            }
        }

        // Compress with zlib/deflate.
        ByteArrayOutputStream idatBaos = new ByteArrayOutputStream(Math.max(32 * 1024, raw.length / 4));
        Deflater def = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(idatBaos, def)) {
            dos.write(raw);
        }
        byte[] idat = idatBaos.toByteArray();

        ByteArrayOutputStream png = new ByteArrayOutputStream(64 * 1024);
        try (DataOutputStream out = new DataOutputStream(png)) {
            // PNG signature
            out.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

            // IHDR
            ByteArrayOutputStream ihdrBaos = new ByteArrayOutputStream(13);
            try (DataOutputStream ihdr = new DataOutputStream(ihdrBaos)) {
                ihdr.writeInt(w);
                ihdr.writeInt(h);
                ihdr.writeByte(8);  // bit depth
                ihdr.writeByte(6);  // color type RGBA
                ihdr.writeByte(0);  // compression
                ihdr.writeByte(0);  // filter
                ihdr.writeByte(0);  // interlace
            }
            writeChunk(out, "IHDR", ihdrBaos.toByteArray());
            writeChunk(out, "IDAT", idat);
            writeChunk(out, "IEND", new byte[0]);
        }
        return png.toByteArray();
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(data.length);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt((int) crc.getValue());
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
