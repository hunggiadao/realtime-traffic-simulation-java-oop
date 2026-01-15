import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Rendering + interaction logic for {@link MapView}.
 *
 * This class exists only to split MapView into smaller files.
 * It intentionally preserves MapView's existing behavior.
 */
final class MapViewRender {
    private MapViewRender() {}

    static void layoutCanvas(MapView view) {
        view.backgroundCanvas.setWidth(view.getWidth());
        view.backgroundCanvas.setHeight(view.getHeight());
        view.canvas.setWidth(view.getWidth());
        view.canvas.setHeight(view.getHeight());
        computeBaseScale(view);
        clampOffsets(view);
        view.backgroundDirty = true;
        redraw(view);
    }

    static void redraw(MapView view) {
        redrawBackgroundIfNeeded(view);
        redrawOverlay(view);
    }

    static void updateBusStops(MapView view, List<String[]> busStopData) {
        view.busStops.clear();
        if (busStopData != null) {
            for (String[] data : busStopData) {
                if (data != null && data.length >= 5) {
                    try {
                        String id = data[0];
                        String name = data[1];
                        String laneId = data[2];
                        double startPos = Double.parseDouble(data[3]);
                        double endPos = Double.parseDouble(data[4]);
                        BusStopMarker marker = new BusStopMarker(id, name, laneId, startPos, endPos);

                        // Get lane geometry for positioning
                        LaneShape lane = view.lanesById.get(laneId);
                        if (lane != null && lane.polyline != null && lane.polyline.size() >= 2) {
                            marker.laneWidth = lane.widthMeters;

                            // Calculate total lane length
                            double laneLength = 0.0;
                            for (int i = 1; i < lane.polyline.size(); i++) {
                                laneLength += lane.polyline.get(i).distance(lane.polyline.get(i - 1));
                            }
                            if (laneLength > 0.001) {
                                // Anchor to the START of the stop segment.
                                // SUMO stops are defined as [startPos, endPos] along a lane; vehicles stop at that segment.
                                // By anchoring to startPos and drawing a rectangle of stopLength forward,
                                // the bus will visually sit "inside" the stop area.
                                double tStart = Math.max(0.0, Math.min(1.0, startPos / laneLength));
                                Point2D lanePosStart = MapViewGeometry.pointAlongPolyline(lane.polyline, tStart);
                                if (lanePosStart != null) {
                                    marker.worldPos = lanePosStart;
                                    marker.direction = MapViewGeometry.laneTangentAt(lanePosStart, lane.polyline);
                                }
                            }
                        }

                        // Only add markers with valid computed positions
                        if (marker.worldPos != null) {
                            view.busStops.add(marker);
                        }
                    } catch (NumberFormatException ignored) {
                        // Skip entries with invalid numeric data
                    }
                }
            }
        }
        MapView.LOGGER.info("Loaded " + view.busStops.size() + " bus stops for map rendering");
        view.backgroundDirty = true;
        redraw(view);
    }

    static void scheduleOverlayRedraw(MapView view) {
        if (view.overlayRedrawScheduled) return;
        view.overlayRedrawScheduled = true;
        Platform.runLater(() -> {
            view.overlayRedrawScheduled = false;
            redrawOverlay(view);
        });
    }

    static void computeBaseScale(MapView view) {
        double w = view.getWidth();
        double h = view.getHeight();
        if (w <= 0 || h <= 0) {
            view.baseScale = 1.0;
            return;
        }
        double scaleX = (w - 2 * view.padding) / (view.maxX - view.minX + 1e-6);
        double scaleY = (h - 2 * view.padding) / (view.maxY - view.minY + 1e-6);
        view.baseScale = Math.max(0.0001, Math.min(scaleX, scaleY));
    }

    static void enableInteractions(MapView view) {
        view.setOnScroll(evt -> {
            double factor = (evt.getDeltaY() > 0) ? 1.1 : 0.9;
            zoom(view, factor, evt.getX(), evt.getY());
            evt.consume();
        });

        view.setOnMousePressed(evt -> {
            view.lastMouseX = evt.getX();
            view.lastMouseY = evt.getY();
        });

        view.setOnMouseDragged(evt -> {
            double dx = evt.getX() - view.lastMouseX;
            double dy = evt.getY() - view.lastMouseY;
            view.offsetX += dx;
            view.offsetY += dy;
            clampOffsets(view);
            view.lastMouseX = evt.getX();
            view.lastMouseY = evt.getY();
            view.backgroundDirty = true;
            redraw(view);
        });
    }

    static void zoom(MapView view, double factor, double pivotX, double pivotY) {
        double oldScale = view.userScale;
        double newScale = view.clamp(view.userScale * factor, 1.0, 25.0);
        factor = newScale / oldScale;

        double scale = view.baseScale * oldScale;
        double worldX = (pivotX - view.padding - view.offsetX) / scale;
        double worldY = (pivotY - view.padding - view.offsetY) / scale;

        view.userScale = newScale;

        double newScaleCombined = view.baseScale * view.userScale;
        view.offsetX = pivotX - view.padding - worldX * newScaleCombined;
        view.offsetY = pivotY - view.padding - worldY * newScaleCombined;
        clampOffsets(view);
        view.backgroundDirty = true;
        redraw(view);
    }

    static void clampOffsets(MapView view) {
        // This function limits panning so the map stays within a reasonable "sandbox".
        // It prevents dragging so far that the network becomes hard to find again.
        double w = view.canvas.getWidth();
        double h = view.canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        if (view.lanes.isEmpty()) return;

        double scale = view.baseScale * view.userScale;
        double contentW = Math.max(0.0, (view.maxX - view.minX) * scale);
        double contentH = Math.max(0.0, (view.maxY - view.minY) * scale);

        // Allow some overscroll margin so the user can inspect edges without losing the map.
        // Make the margin scale with zoom (in world meters) so it feels consistent.
        // When zoomed out, tighten the sandbox aggressively to avoid getting "lost".
        double marginMeters = 35.0;
        double z = view.clamp(view.userScale, 1.0, 25.0);
        double zoomOutT = view.clamp((z - 1.0) / (3.0 - 1.0), 0.0, 1.0);
        double minMarginWhenZoomedOut = Math.min(w, h) * 0.35;
        double minMargin = (1.0 - zoomOutT) * minMarginWhenZoomedOut + zoomOutT * 80.0;
        double margin = view.clamp(marginMeters * scale, minMargin, Math.min(360.0, Math.min(w, h) * 0.30));

        // Screen bounds of the network: [padding + offsetX, padding + offsetX + contentW]
        double minOffX = margin - view.padding - contentW;
        double maxOffX = (w - margin) - view.padding;
        if (minOffX > maxOffX) {
            // If the content is smaller than the viewport (at this zoom), keep it centered.
            view.offsetX = (w - contentW) * 0.5 - view.padding;
        } else {
            view.offsetX = view.clamp(view.offsetX, minOffX, maxOffX);
        }

        // Y uses the same screen-space logic because transform() flips world Y inside the mapping.
        double minOffY = margin - view.padding - contentH;
        double maxOffY = (h - margin) - view.padding;
        if (minOffY > maxOffY) {
            view.offsetY = (h - contentH) * 0.5 - view.padding;
        } else {
            view.offsetY = view.clamp(view.offsetY, minOffY, maxOffY);
        }
    }

    private static void redrawBackgroundIfNeeded(MapView view) {
        if (!view.backgroundDirty) return;

        double w = view.backgroundCanvas.getWidth();
        double h = view.backgroundCanvas.getHeight();
        GraphicsContext g = view.backgroundCanvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, w, h);
        if (w <= 0 || h <= 0) return;

        double scale = view.baseScale * view.userScale;

        // No network loaded? Show hint.
        if (view.lanes.isEmpty()) {
            g.setFill(Color.web("#888888"));
            g.fillText("No network loaded. Check your .sumocfg and net path.", 20, 30);
            view.backgroundDirty = false;
            return;
        }

        // Green grass background after config is loaded (network exists)
        g.setFill(Color.web("#e8f5e9"));
        g.fillRect(0, 0, w, h);

        Color roadEdge = Color.web("#000000");
        Color roadFill = Color.web("#101010");
        Color laneLine = Color.web("#d0d0d0");
        // SUMO "standard" scheme
        // - bicycle lanes: orange/brown
        // - pedestrian paths/sidewalks: gray
        Color bikeLaneFill = Color.web("#d7a08b");
        Color pedPathFill = Color.web("#bdbdbd");

        // Default stroke style for the scene.
        g.setLineJoin(StrokeLineJoin.ROUND);

        // Junction polygons (base intersection surface).
        // This is drawn BEFORE lane borders/separators so the junction doesn't become a gray "blob".
        // SUMO-GUI typically shows a clean junction surface; connector internals are optional.
        g.setFill(roadFill);
        for (JunctionShape j : view.junctions) {
            if (j.polygon.isEmpty()) continue;
            int n = j.polygon.size();
            double[] xs = new double[n];
            double[] ys = new double[n];
            for (int i = 0; i < n; i++) {
                Point2D tp = transform(view, j.polygon.get(i), h, scale);
                xs[i] = tp.getX();
                ys[i] = tp.getY();
            }
            g.fillPolygon(xs, ys, n);
        }

        // Lanes/roads
        // PERF/QUALITY:
        // Rendering each lane with a thick outer border causes double-drawn borders between adjacent lanes
        // (two lane boundaries overlap), which makes roads look different from SUMO-GUI.
        // Instead:
        //  1) Draw the road fill for all lanes.
        //  2) Draw ONLY the outer road border (outermost lanes).
        //  3) Draw ONE separator per lane boundary (no duplicates).

        // Precompute lane-index ranges per non-internal edge.
        Map<String, int[]> edgeLaneRange = new HashMap<>(); // edgeId -> [minIdx,maxIdx]
        Map<String, Map<Integer, LaneShape>> edgeLanesByIndex = new HashMap<>(); // edgeId -> (laneIndex -> lane)
        for (LaneShape lane : view.lanes) {
            if (lane == null || lane.edgeId == null || lane.edgeId.isEmpty()) continue;
            if (lane.edgeId.startsWith(":")) continue; // internal lanes handled separately
            if (lane.laneIndex < 0) continue;
            edgeLanesByIndex.computeIfAbsent(lane.edgeId, k -> new HashMap<>()).put(lane.laneIndex, lane);
            int[] mm = edgeLaneRange.get(lane.edgeId);
            if (mm == null) {
                edgeLaneRange.put(lane.edgeId, new int[]{lane.laneIndex, lane.laneIndex});
            } else {
                if (lane.laneIndex < mm[0]) mm[0] = lane.laneIndex;
                if (lane.laneIndex > mm[1]) mm[1] = lane.laneIndex;
            }
        }

        // Roads: use square/flat caps so road ends look like rectangles (closer to SUMO GUI).
        // We restore ROUND caps later for arrows/markers.
        g.setLineCap(StrokeLineCap.BUTT);

        // Pass 1: draw road fill (continuous areas).
        // Draw motor lanes first, then special lanes (bike/ped) on top so their colors are not covered.
        for (int pass = 0; pass < 2; pass++) {
            for (LaneShape lane : view.lanes) {
                if (lane == null || lane.polyline == null || lane.polyline.size() < 2) continue;
                double laneScreenWidth = Math.max(1.5, lane.widthMeters * scale);
                boolean internal = (lane.edgeId != null) && lane.edgeId.startsWith(":");

                if (internal) {
                    // Internal connector lanes are rendered later (after junction fill) so they don't clutter the intersection.
                    continue;
                }

                boolean motor = LaneShape.isMotorLane(lane);
                if ((pass == 0) != motor) continue;

                // Non-internal lanes: draw fill only (no per-lane outer border).
                // Slightly widen fill to hide tiny gaps between strokes, but keep it small to avoid
                // double-overdraw bands (which can look like a "super dark" separator).
                Color fill = roadFill;
                if (lane.pedestrianOnly) fill = pedPathFill;
                if (lane.bikeOnly) fill = bikeLaneFill;
                strokePolyline(view, g, lane.polyline, h, scale, laneScreenWidth + 0.25, fill, null, StrokeLineCap.BUTT);
            }
        }

        // Pass 2: draw borders + separators for non-internal edges.
        for (LaneShape lane : view.lanes) {
            if (lane == null || lane.polyline == null || lane.polyline.size() < 2) continue;
            if (lane.edgeId == null || lane.edgeId.isEmpty()) continue;
            if (lane.edgeId.startsWith(":")) continue;
            if (lane.laneIndex < 0) continue;
            int[] mm = edgeLaneRange.get(lane.edgeId);
            if (mm == null) continue;

            double laneScreenWidth = Math.max(1.5, lane.widthMeters * scale);

            // Outer border: only for outermost lanes.
            double borderW = view.clamp(laneScreenWidth * 0.22, 1.4, 2.8);
            if (lane.laneIndex == mm[0]) {
                // Rightmost lane (SUMO lane index 0 is rightmost in edge direction): draw right border.
                drawLaneSingleEdge(view, g, lane.polyline, h, scale, laneScreenWidth, roadEdge, borderW, -1);
            }
            if (lane.laneIndex == mm[1]) {
                // Leftmost lane: draw left border.
                drawLaneSingleEdge(view, g, lane.polyline, h, scale, laneScreenWidth, roadEdge, borderW, +1);
            }

            // Lane separator: draw one boundary per lane (between this lane and its neighbor).
            // Using the right edge avoids duplicates when indices increase leftwards.
            if (lane.laneIndex > mm[0]) {
                double sepW = view.clamp(laneScreenWidth * 0.12, 0.9, 1.6);
                boolean dash = false;
                LaneShape neighbor = null;
                Map<Integer, LaneShape> byIdx = edgeLanesByIndex.get(lane.edgeId);
                if (byIdx != null) {
                    // Find the immediate neighbor lane on the "right".
                    // Some nets use non-contiguous lane indices, so laneIndex-1 may not exist.
                    neighbor = byIdx.get(lane.laneIndex - 1);
                    if (neighbor == null) {
                        for (int idx = lane.laneIndex - 2; idx >= mm[0]; idx--) {
                            neighbor = byIdx.get(idx);
                            if (neighbor != null) break;
                        }
                    }
                    // User rule:
                    // - Dashed means 2+ lanes in SAME direction (within the same edge).
                    // - Solid center divider means OPPOSITE directions (handled in Pass 2b).
                    // Therefore: only dash between motor lanes within the edge.
                    if (neighbor != null && LaneShape.isMotorLane(lane) && LaneShape.isMotorLane(neighbor)) {
                        dash = true;
                    }
                } else {
                    dash = false;
                }
                if (dash) {
                    // SUMO shows same-direction multi-lane separators as dashed lines between lanes.
                    // Use the midline between the two lane center polylines, not an offset boundary,
                    // because some nets have irregular lane geometries where boundary-offset is wrong.
                    List<Point2D> bAligned = (neighbor != null) ? neighbor.polyline : null;
                    if (bAligned != null && lane.polyline != null && lane.polyline.size() >= 2 && bAligned.size() >= 2) {
                        double dStart = lane.polyline.get(0).distance(bAligned.get(0));
                        double dEnd = lane.polyline.get(0).distance(bAligned.get(bAligned.size() - 1));
                        if (dEnd < dStart) {
                            bAligned = new ArrayList<>(bAligned);
                            java.util.Collections.reverse(bAligned);
                        }
                    }
                    List<Point2D> center = buildAveragedCenterline(lane.polyline, bAligned, 32);
                    if (center.size() >= 2) {
                        strokePolyline(view, g, center, h, scale,
                                view.clamp(sepW, 1.0, 1.8),
                                Color.web("#f2f2f2").deriveColor(0, 1, 1, 0.90),
                                new double[]{12, 12},
                                StrokeLineCap.BUTT);
                    }
                } else {
                    drawLaneSingleEdge(view, g, lane.polyline, h, scale, laneScreenWidth, laneLine, sepW, -1);
                }
            }
        }

        // Pass 2b: solid centerline for two-way roads (opposite-direction edge pairs).
        // SUMO draws a solid divider between opposite-direction edges (A: from->to, B: to->from).
        // We pair edges using net topology (from/to IDs) rather than geometry heuristics to avoid
        // incorrect cross-pairing that can "destroy" the map.
        {
            final class EdgeRep {
                final String edgeId;
                final List<LaneShape> motorLanes;
                final Point2D start;
                final Point2D end;
                final double len;
                final Point2D dir;

                EdgeRep(String edgeId, List<LaneShape> motorLanes) {
                    this.edgeId = edgeId;
                    this.motorLanes = motorLanes;
                    List<Point2D> p = motorLanes.get(0).polyline;
                    this.start = p.get(0);
                    this.end = p.get(p.size() - 1);
                    this.len = polylineLength(p);
                    // Use a direction estimate away from the junction endpoints.
                    // Endpoints can include curved junction geometry which makes opposite edges
                    // fail the dot-product test even though they are true two-way pairs.
                    this.dir = polylineDirectionMid(p);
                }
            }

            final class LanePair {
                final LaneShape aLane;
                final List<Point2D> bAligned;
                final double avgDist;
                LanePair(LaneShape aLane, List<Point2D> bAligned, double avgDist) {
                    this.aLane = aLane;
                    this.bAligned = bAligned;
                    this.avgDist = avgDist;
                }
            }

            java.util.function.BiFunction<EdgeRep, EdgeRep, LanePair> bestLanePair = (a, b) -> {
                if (a == null || b == null) return null;
                if (a.motorLanes == null || b.motorLanes == null) return null;
                if (a.motorLanes.isEmpty() || b.motorLanes.isEmpty()) return null;
                LaneShape bestA = null;
                List<Point2D> bestBAligned = null;
                double bestDist = Double.POSITIVE_INFINITY;

                for (LaneShape la : a.motorLanes) {
                    if (la == null || la.polyline == null || la.polyline.size() < 2) continue;
                    for (LaneShape lb : b.motorLanes) {
                        if (lb == null || lb.polyline == null || lb.polyline.size() < 2) continue;

                        List<Point2D> bAligned = lb.polyline;
                        double dStart = la.polyline.get(0).distance(bAligned.get(0));
                        double dEnd = la.polyline.get(0).distance(bAligned.get(bAligned.size() - 1));
                        if (dEnd < dStart) {
                            bAligned = new ArrayList<>(bAligned);
                            java.util.Collections.reverse(bAligned);
                        }

                        double avgDist = averagePolylineDistanceMid(la.polyline, bAligned, 18);
                        if (avgDist < bestDist) {
                            bestDist = avgDist;
                            bestA = la;
                            bestBAligned = bAligned;
                        }
                    }
                }

                if (bestA == null || bestBAligned == null || !Double.isFinite(bestDist)) return null;
                return new LanePair(bestA, bestBAligned, bestDist);
            };

            Map<String, EdgeRep> repByEdge = new HashMap<>();
            for (Map.Entry<String, int[]> e : edgeLaneRange.entrySet()) {
                String edgeId = e.getKey();
                int[] mm = e.getValue();
                Map<Integer, LaneShape> byIdx = edgeLanesByIndex.get(edgeId);
                if (byIdx == null) continue;

                // Collect all MOTOR lanes for this edge.
                // We'll later choose the lane that is closest to the opposite edge so the divider
                // lands on the true median (works for 1-lane and multi-lane roads).
                List<LaneShape> motor = new ArrayList<>();
                for (int idx = mm[0]; idx <= mm[1]; idx++) {
                    LaneShape cand = byIdx.get(idx);
                    if (cand == null) continue;
                    if (!LaneShape.isMotorLane(cand)) continue;
                    if (cand.polyline == null || cand.polyline.size() < 2) continue;
                    motor.add(cand);
                }
                // If the edge has no motor lane at all (pure sidewalk/bike edge), skip it.
                if (!motor.isEmpty()) {
                    // Keep ordering stable (lower lane index first) if possible.
                    motor.sort(Comparator.comparingInt(ls -> ls.laneIndex));
                    repByEdge.put(edgeId, new EdgeRep(edgeId, motor));
                }
            }

            Map<String, List<EdgeRep>> repsByFromTo = new HashMap<>();
            for (EdgeRep r : repByEdge.values()) {
                EdgeInfo info = view.edgesById.get(r.edgeId);
                if (info == null) continue;
                if (info.from.isEmpty() || info.to.isEmpty()) continue;
                String key = info.from + "->" + info.to;
                repsByFromTo.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }

            // Pair each edge with candidates that swap from/to; choose the best local match.
            Map<String, String> pairs = new HashMap<>();
            for (EdgeRep a : repByEdge.values()) {
                if (pairs.containsKey(a.edgeId)) continue;
                EdgeInfo ai = view.edgesById.get(a.edgeId);
                if (ai == null || ai.from.isEmpty() || ai.to.isEmpty()) continue;

                List<EdgeRep> candidates = repsByFromTo.get(ai.to + "->" + ai.from);
                if (candidates == null || candidates.isEmpty()) continue;

                // Strong preference: SUMO often uses opposite IDs like E62 and -E62.
                // If that counterpart exists and is a candidate, pick it.
                String preferredId = a.edgeId.startsWith("-") ? a.edgeId.substring(1) : ("-" + a.edgeId);
                EdgeRep preferred = repByEdge.get(preferredId);
                if (preferred != null && candidates.contains(preferred) && !pairs.containsKey(preferred.edgeId)) {
                    pairs.put(a.edgeId, preferred.edgeId);
                    pairs.put(preferred.edgeId, a.edgeId);
                    continue;
                }

                EdgeRep best = null;
                double bestScore = Double.POSITIVE_INFINITY;

                for (EdgeRep b : candidates) {
                    if (b == null) continue;
                    if (b.edgeId.equals(a.edgeId)) continue;
                    if (pairs.containsKey(b.edgeId)) continue;

                    // Must be opposite direction visually.
                    double dot = a.dir.getX() * b.dir.getX() + a.dir.getY() * b.dir.getY();
                    if (dot > -0.55) continue;

                    // Score using the closest motor-lane pair between the two edges.
                    LanePair lp = bestLanePair.apply(a, b);
                    if (lp == null) continue;
                    double avgDist = lp.avgDist;
                    double lenDiff = Math.abs(a.len - b.len);
                    double score = avgDist + 0.05 * lenDiff;

                    // Skip obviously-wrong pairs (too far apart).
                    double maxDist = Math.max(7.0, Math.min(35.0, Math.min(a.len, b.len) * 0.20));
                    if (avgDist > maxDist) continue;

                    if (score < bestScore) {
                        bestScore = score;
                        best = b;
                    }
                }

                if (best != null) {
                    pairs.put(a.edgeId, best.edgeId);
                    pairs.put(best.edgeId, a.edgeId);
                }
            }

            // Draw each pair once.
            java.util.HashSet<String> drawn = new java.util.HashSet<>();
            for (Map.Entry<String, String> p : pairs.entrySet()) {
                String aId = p.getKey();
                String bId = p.getValue();
                if (aId == null || bId == null) continue;
                String canonical = (aId.compareTo(bId) < 0) ? (aId + "|" + bId) : (bId + "|" + aId);
                if (drawn.contains(canonical)) continue;
                drawn.add(canonical);

                EdgeRep a = repByEdge.get(aId);
                EdgeRep b = repByEdge.get(bId);
                if (a == null || b == null) continue;

                LanePair lp = bestLanePair.apply(a, b);
                if (lp == null) continue;
                List<Point2D> center = buildAveragedCenterline(lp.aLane.polyline, lp.bAligned, 30);
                if (center.size() < 2) continue;

                double wPx = view.clamp(1.4 * view.userScale, 1.2, 2.6);
                strokePolyline(view, g, center, h, scale,
                        wPx,
                        Color.web("#f2f2f2").deriveColor(0, 1, 1, 0.95),
                        null,
                        StrokeLineCap.BUTT);
            }
        }

        // Pass 2c: dashed bicycle connector lanes inside junctions (SUMO-style).
        // SUMO shows bicycle turning connectors as dashed white curves over the junction surface.
        // These are typically internal lanes whose edgeId starts with ':' and allow bicycles.
        {
            double dashLen = view.clamp(6.0 * Math.sqrt(view.clamp(view.userScale, 1.0, 25.0)), 5.0, 14.0);
            double[] dashes = new double[]{dashLen, dashLen};
            Color guide = Color.web("#f2f2f2").deriveColor(0, 1, 1, 0.75);

            for (LaneShape lane : view.lanes) {
                if (lane == null || lane.polyline == null || lane.polyline.size() < 2) continue;
                if (lane.edgeId == null || !lane.edgeId.startsWith(":")) continue;
                if (lane.pedestrianOnly) continue;

                // Draw only internal lanes that are actual bicycle connectors according to SUMO connections,
                // plus any internal lanes that are explicitly bike-only.
                boolean isBikeConnector = lane.bikeOnly;
                if (!isBikeConnector && lane.laneId != null && !lane.laneId.isEmpty()) {
                    isBikeConnector = view.bicycleConnectorLaneIds.contains(lane.laneId);
                }
                if (!isBikeConnector) continue;

                double laneScreenWidth = Math.max(1.5, lane.widthMeters * scale);
                double wPx = view.clamp(laneScreenWidth * 0.10, 0.9, 1.7);

                // Use the previous style: dashed boundaries (reads like a dedicated connector lane).
                drawLaneSingleEdge(view, g, lane.polyline, h, scale, laneScreenWidth, guide, wPx, -1, dashes);
                drawLaneSingleEdge(view, g, lane.polyline, h, scale, laneScreenWidth, guide, wPx, +1, dashes);
            }
        }

        // Restore round caps for small UI strokes (arrows, labels, markers).
        g.setLineCap(StrokeLineCap.ROUND);

        // NOTE: We only draw bicycle connector internals (not all connectors),
        // to match SUMO's visuals without cluttering the junction.

        drawLabels(view, g, h, scale);
        drawRoadDirectionArrows(view, g, h, scale, Color.web("#bdbdbd"));
        drawBusStops(view, g, h, scale);

        view.backgroundDirty = false;
    }

    private static void redrawOverlay(MapView view) {
        double w = view.canvas.getWidth();
        double h = view.canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        // Compute a single smoothing alpha for this redraw (prevents per-vehicle exp/nanoTime cost).
        long nowNs = System.nanoTime();
        double dtSec = (view.lastOverlayRedrawNs == 0L) ? 0.0 : Math.max(0.0, (nowNs - view.lastOverlayRedrawNs) / 1_000_000_000.0);
        view.lastOverlayRedrawNs = nowNs;
        final double tauSec = 0.14; // smaller => more responsive; larger => smoother
        double alpha = (dtSec <= 0.0) ? 0.30 : (1.0 - Math.exp(-dtSec / tauSec));
        view.headingSmoothingAlpha = view.clamp(alpha, 0.18, 0.95);

        GraphicsContext g = view.canvas.getGraphicsContext2D();
        // Overlay is transparent; clear only this canvas.
        g.clearRect(0, 0, w, h);

        double scale = view.baseScale * view.userScale;
        if (view.lanes.isEmpty()) return;

        // Vehicles
        if (view.vehiclePositions != null) {
            for (Entry<String, Point2D> e : view.vehiclePositions.entrySet()) {
                String vehicleId = e.getKey();
                Color c = Color.RED;
                if (view.vehicleColors != null) {
                    Color mapped = view.vehicleColors.get(vehicleId);
                    if (mapped != null) c = mapped;
                }

                Point2D worldPos = e.getValue();
                Point2D drawWorld = worldPos;

                LaneShape laneForHeading = null;
                if (view.vehicleLaneIds != null) {
                    String laneId = view.vehicleLaneIds.get(vehicleId);
                    LaneShape lane = (laneId != null) ? view.lanesById.get(laneId) : null;
                    laneForHeading = lane;
                }

                Point2D tp = transform(view, drawWorld, h, scale);

                Double angleDegrees = (view.vehicleAngles != null) ? view.vehicleAngles.get(vehicleId) : null;

                String vehicleType = "car";
                if (view.vehicleTypes != null && view.vehicleTypes.containsKey(vehicleId)) {
                    vehicleType = view.vehicleTypes.get(vehicleId).toLowerCase();
                }

                drawVehicleShape(view, g, vehicleId, worldPos, laneForHeading, tp.getX(), tp.getY(), angleDegrees, vehicleType, c, view.userScale);
            }
        }

        // Traffic light stop lines
        drawTrafficLightStopLines(view, g, h, scale);
    }

    private static void drawTrafficLightStopLines(MapView view, GraphicsContext g, double height, double scale) {
        // This function draws stop lines at the end of each signaled lane.
        // Sizes are clamped in screen pixels so they don't become huge when zooming.
        if (view.laneSignalColors == null || view.laneSignalColors.isEmpty()) return;

        for (Entry<String, Color> e : view.laneSignalColors.entrySet()) {
            LaneShape lane = view.lanesById.get(e.getKey());
            if (lane == null || lane.polyline == null || lane.polyline.size() < 2) continue;
            int n = lane.polyline.size();

            Point2D worldPrev = lane.polyline.get(n - 2);
            Point2D worldEnd = lane.polyline.get(n - 1);
            Point2D pPrev = transform(view, worldPrev, height, scale);
            Point2D pEnd = transform(view, worldEnd, height, scale);

            double dx = pEnd.getX() - pPrev.getX();
            double dy = pEnd.getY() - pPrev.getY();
            double len = Math.hypot(dx, dy);
            if (len < 0.001) continue;
            double ux = dx / len;
            double uy = dy / len;
            // Normal vector for stop line across lane direction
            double nx = -uy;
            double ny = ux;

            // Keep stop-line thickness mostly constant in screen pixels.
            // Only increase *length* with zoom so the stop line is more visible when zoomed in.
            double lanePx = view.clamp(Math.max(6.0, lane.widthMeters * view.baseScale), 6.0, 22.0);

            // Make the stop-line longer when zoomed in, without increasing thickness.
            // Use an "ease-in" curve so mid-zoom doesn't produce overly long lines,
            // while max zoom still gets a clearly longer stop line.
            // NOTE: userScale ranges up to ~25 in this project.
            // We want the stop line to be slightly longer at mid zoom (not tiny),
            // and noticeably longer near max zoom, without becoming road-wide too early.
            double z = view.clamp(view.userScale, 1.0, 25.0);

            // Stage 1: small boost up to around userScale ~8.
            double t1 = view.clamp((z - 1.0) / (8.0 - 1.0), 0.0, 1.0);
            t1 = t1 * t1 * (3.0 - 2.0 * t1); // smoothstep

            // Stage 2: stronger ramp from ~8 up to max zoom.
            double t2 = view.clamp((z - 8.0) / (25.0 - 8.0), 0.0, 1.0);
            t2 = t2 * t2;

            double baseLen = view.clamp(lanePx * 1.2, 14.0, 44.0);
            double midExtraPx = 18.0;
            double maxExtra = view.clamp(lanePx * 3.2, 35.0, 90.0);
            double lineLen = view.clamp(baseLen + midExtraPx * t1 + maxExtra * t2, 14.0, 120.0);

            // Place the stop line a small, consistent distance before the lane end in *world meters*.
            // If this is in pixels, the world-distance changes with zoom and cars may appear to cross it.
            double backShiftMeters = view.clamp(lane.widthMeters * 0.20, 0.35, 0.85);
            double backShiftPx = backShiftMeters * scale;
            double cx = pEnd.getX() - ux * backShiftPx;
            double cy = pEnd.getY() - uy * backShiftPx;

            double x1 = cx - nx * (lineLen / 2.0);
            double y1 = cy - ny * (lineLen / 2.0);
            double x2 = cx + nx * (lineLen / 2.0);
            double y2 = cy + ny * (lineLen / 2.0);

            Color c = (e.getValue() != null) ? e.getValue() : Color.RED;
            g.setLineDashes();

            // Outline for readability on dark roads
            g.setStroke(Color.WHITE);
            g.setLineWidth(view.clamp(lanePx * 0.55, 3.2, 14.0));
            g.strokeLine(x1, y1, x2, y2);

            g.setStroke(c);
            g.setLineWidth(view.clamp(lanePx * 0.35, 2.0, 11.0));
            g.strokeLine(x1, y1, x2, y2);
        }
    }

    private static void drawBusStops(MapView view, GraphicsContext g, double height, double scale) {
        if (view.busStops == null || view.busStops.isEmpty()) return;

        // Colors for bus stop
        Color signYellow = Color.web("#FFD600");   // Yellow "H" sign
        Color signBorder = Color.web("#333333");   // Dark border
        Color waitingArea = Color.web("#B0BEC5"); // Light gray waiting area

        for (BusStopMarker stop : view.busStops) {
            if (stop.worldPos == null) continue;

            // Transform bus stop world position (on the lane) to screen
            Point2D tp = transform(view, stop.worldPos, height, scale);
            double cx = tp.getX();
            double cy = tp.getY();

            // Get lane direction for orientation
            Point2D dirWorld = (stop.direction != null) ? stop.direction : new Point2D(1.0, 0.0);
            double dirLen = Math.hypot(dirWorld.getX(), dirWorld.getY());
            double fx, fy; // forward direction (along lane)
            if (dirLen < 1e-9) {
                fx = 1.0; fy = 0.0;
            } else {
                fx = dirWorld.getX() / dirLen;
                fy = -dirWorld.getY() / dirLen; // flip Y for screen
            }
            // rx, ry = right perpendicular
            // NEGATIVE to go to OUTER sidewalk (not median)
            double rx = -fy;
            double ry = fx;

            // Size based on zoom
            // IMPORTANT: allow shrinking when zoomed out (userScale < 1)
            // so bus stops don't become huge/overlapping at far zoom.
            double baseSize = view.clamp(6.0 + view.userScale * 2.0, 6.0, 30.0);
            double laneWidthPx = stop.laneWidth * scale;

            // Zoom boost for "max zoom only" thickness increase
            // 0.0 at <=2x zoom, approaches 1.0 around >=4x zoom.
            double zoomBoost = view.clamp((view.userScale - 2.0) / 2.0, 0.0, 1.0);

            // --- Draw waiting area (long and thick rectangle at road edge) ---
            // The stop rectangle represents the stop segment length.
            // Clamp prevents it being too long at far zoom, but allow bigger at max zoom
            // so buses visually fit into the station area.
            double lenMul = 0.90 + 0.30 * zoomBoost; // 0.90 (far) -> 1.20 (max zoom)
            double maxLen = baseSize * (8.0 + 10.0 * zoomBoost); // 8x..18x baseSize
            double areaLen = view.clamp(stop.stopLength * scale * lenMul, baseSize * 2.0, maxLen);

            // Make it thicker mainly at high zoom.
            double areaWid = baseSize * (0.65 + 0.35 * zoomBoost); // 0.65..1.00

            // Offset just outside the road edge (not overlapping road)
            double sidewalkOffset = laneWidthPx * 0.55 + areaWid * 0.5;

            // Anchor point is stop START on the lane. Shift half of the area forward so the
            // rectangle covers [start..end] rather than being centered at the start.
            double rectCx = cx + fx * (areaLen * 0.5);
            double rectCy = cy + fy * (areaLen * 0.5);

            // Apply sidewalk offset after the forward shift.
            double signX = rectCx + rx * sidewalkOffset;
            double signY = rectCy + ry * sidewalkOffset;
            g.setFill(waitingArea);
            g.setStroke(Color.GRAY);
            g.setLineWidth(1.0);
            g.setLineDashes();
            // Rectangle aligned with lane direction
            double[] areaX = new double[4];
            double[] areaY = new double[4];
            areaX[0] = signX + fx * areaLen/2 - rx * areaWid/2;
            areaY[0] = signY + fy * areaLen/2 - ry * areaWid/2;
            areaX[1] = signX + fx * areaLen/2 + rx * areaWid/2;
            areaY[1] = signY + fy * areaLen/2 + ry * areaWid/2;
            areaX[2] = signX - fx * areaLen/2 + rx * areaWid/2;
            areaY[2] = signY - fy * areaLen/2 + ry * areaWid/2;
            areaX[3] = signX - fx * areaLen/2 - rx * areaWid/2;
            areaY[3] = signY - fy * areaLen/2 - ry * areaWid/2;
            g.fillPolygon(areaX, areaY, 4);
            g.strokePolygon(areaX, areaY, 4);

            // --- Draw "H" sign at CENTER of waiting area ---
            double hSize = view.clamp(baseSize * 0.6, 8.0, 20.0);
            double hX = signX;  // Center of rectangle
            double hY = signY;  // Center of rectangle

            // Yellow circle with "H"
            g.setFill(signYellow);
            g.fillOval(hX - hSize/2, hY - hSize/2, hSize, hSize);
            g.setStroke(signBorder);
            g.setLineWidth(1.5);
            g.strokeOval(hX - hSize/2, hY - hSize/2, hSize, hSize);

            // Draw "H" letter
            g.setFill(signBorder);
            double fontSize = hSize * 0.6;
            g.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));
            g.fillText("H", hX - fontSize * 0.3, hY + fontSize * 0.35);

            // --- Draw label with the SAME behavior as road/traffic labels ---
            // drawLabels() hides labels when scale < 0.8 and uses a zoom-scaled font.
            if (scale >= 0.8) {
                String label = (stop.name != null && !stop.name.isEmpty()) ? stop.name : stop.id;
                if (label != null && !label.isEmpty()) {
                    Font oldFont = g.getFont();
                    double labelFontSize = view.clamp(10.0 + (view.userScale - 1.0) * 2.0, 10.0, 16.0);
                    g.setFont(Font.font(labelFontSize));

                    // Match the same label style as road edges and traffic lights
                    drawTag(g, hX + hSize / 2 + 6, hY - hSize / 2 - 6, label, Color.WHITE, Color.web("#333333"));

                    g.setFont(oldFont);
                }
            }
        }
    }

    private static void drawLabels(MapView view, GraphicsContext g, double height, double scale) {
        // Reduce clutter: only show labels when zoomed in enough.
        if (scale < 0.8) return;
        double fontSize = view.clamp(10.0 + (view.userScale - 1.0) * 2.0, 10.0, 16.0);
        g.setFont(Font.font(fontSize));

        // Edge labels
        for (Entry<String, Point2D> e : view.edgeLabelWorldPos.entrySet()) {
            Point2D tp = transform(view, e.getValue(), height, scale);
            drawTag(g, tp.getX(), tp.getY(), e.getKey(), Color.WHITE, Color.web("#333333"));
        }

        // Traffic light labels
        for (TextMarker tl : view.trafficLightMarkers) {
            Point2D tp = transform(view, tl.world, height, scale);
            // Small marker dot
            g.setFill(Color.web("#ffcc00"));
            g.fillOval(tp.getX() - 2.5, tp.getY() - 2.5, 5, 5);
            drawTag(g, tp.getX() + 6, tp.getY() - 6, tl.text, Color.WHITE, Color.web("#333333"));
        }
    }

    private static void drawTag(GraphicsContext g, double x, double y, String text, Color bg, Color fg) {
        if (text == null || text.isEmpty()) return;
        double padX = 4.0;
        double padY = 2.0;
        // Approximate text bounds without measuring: good enough for short IDs.
        double w = padX * 2 + text.length() * g.getFont().getSize() * 0.55;
        double h = padY * 2 + g.getFont().getSize();
        g.setFill(bg);
        g.fillRoundRect(x - padX, y - h + padY, w, h, 6, 6);
        g.setStroke(Color.web("#000000"));
        g.setLineWidth(0.6);
        g.strokeRoundRect(x - padX, y - h + padY, w, h, 6, 6);
        g.setFill(fg);
        g.fillText(text, x, y);
    }

    private static void drawVehicleShape(MapView view, GraphicsContext g, String vehicleId, Point2D worldPos, LaneShape lane,
                                         double x, double y, Double angleDegrees,
                                         String vehicleType, Color color, double scale) {
        // This function computes a stable heading direction:
        // 1) Prefer motion direction (best for lane changes)
        // 2) Prefer lane tangent when speed is ~0 (stable at stops)
        // 3) Prefer SUMO angle (works even if position updates are throttled)
        // For SUMO angle, we auto-detect the convention to avoid 90°-off headings.
        Point2D dirWorld = null;

        Point2D prevWorld = (vehicleId != null) ? view.lastVehicleWorldPos.get(vehicleId) : null;
        Point2D movementDirWorld = null;
        if (prevWorld != null && worldPos != null) {
            double mdx = worldPos.getX() - prevWorld.getX();
            double mdy = worldPos.getY() - prevWorld.getY();
            double mlen = Math.hypot(mdx, mdy);
            if (mlen > 1e-6) {
                movementDirWorld = new Point2D(mdx / mlen, mdy / mlen);
            }
        }

        Point2D laneDirWorld = null;
        if (lane != null && lane.polyline != null && lane.polyline.size() >= 2 && worldPos != null) {
            laneDirWorld = MapViewGeometry.laneTangentAt(worldPos, lane.polyline);
        }

        // (1) Motion direction (world coordinates)
        if (movementDirWorld != null) {
            dirWorld = movementDirWorld;
        }

        // (2) If we're not moving (or missing prev), use lane tangent for stability.
        if (dirWorld == null && laneDirWorld != null) {
            dirWorld = laneDirWorld;
        }

        // (3) SUMO angle (world coordinates) when available.
        if (dirWorld == null && angleDegrees != null) {
            double angleRad = Math.toRadians(angleDegrees);

            // Two common conventions seen in projects:
            // A) 0° = East (+X), 90° = North (+Y)  => (cos, sin)
            // B) 0° = North (+Y), 90° = East (+X)  => (sin, cos)
            Point2D candA = new Point2D(Math.cos(angleRad), Math.sin(angleRad));
            Point2D candB = new Point2D(Math.sin(angleRad), Math.cos(angleRad));

            Point2D ref = (movementDirWorld != null) ? movementDirWorld : laneDirWorld;
            if (ref == null && vehicleId != null) {
                // Use previous smoothed direction as a tie-breaker (convert back to world-like by unflipping Y).
                Point2D prevScreen = view.smoothedVehicleDir.get(vehicleId);
                if (prevScreen != null) {
                    ref = new Point2D(prevScreen.getX(), -prevScreen.getY());
                }
            }

            if (ref != null) {
                double dotA = candA.getX() * ref.getX() + candA.getY() * ref.getY();
                double dotB = candB.getX() * ref.getX() + candB.getY() * ref.getY();
                dirWorld = (dotA >= dotB) ? candA : candB;
            } else {
                dirWorld = candA;
            }
        }

        // (4) Final fallback
        if (dirWorld == null) dirWorld = new Point2D(1.0, 0.0);

        // Convert world direction to screen direction (screen Y is flipped)
        Point2D dirScreen = new Point2D(dirWorld.getX(), -dirWorld.getY());
        dirScreen = MapViewGeometry.normalize(dirScreen);

        // Smooth direction to avoid jitter / sudden flips at junctions.
        if (vehicleId != null) {
            boolean isBus = (vehicleType != null) && vehicleType.toLowerCase().contains("bus");

            // Buses should rotate more responsively; otherwise the back-shift can look like drifting.
            double alpha = isBus ? view.clamp(view.headingSmoothingAlpha * 1.75, 0.35, 0.98) : view.headingSmoothingAlpha;

            Point2D prevDir = view.smoothedVehicleDir.get(vehicleId);
            if (prevDir != null) {
                // If the direction flips ~180°, vector blending can collapse to near-zero.
                // Snap in that case to avoid weird spins.
                double dot = prevDir.getX() * dirScreen.getX() + prevDir.getY() * dirScreen.getY();
                if (dot < -0.55) {
                    prevDir = null;
                }
            }

            if (prevDir != null) {
                Point2D blended = new Point2D(
                        prevDir.getX() * (1.0 - alpha) + dirScreen.getX() * alpha,
                        prevDir.getY() * (1.0 - alpha) + dirScreen.getY() * alpha
                );
                dirScreen = MapViewGeometry.normalize(blended);
            }

            view.smoothedVehicleDir.put(vehicleId, dirScreen);
            if (worldPos != null) {
                view.lastVehicleWorldPos.put(vehicleId, worldPos);
            }
        }

        double fx = dirScreen.getX();
        double fy = dirScreen.getY();
        // Right vector perpendicular to forward (screen coordinates)
        double rx = fy;
        double ry = -fx;

        // Check if this is a bus vehicle (bus type in SUMO typically contains "bus")
        boolean isBus = (vehicleType != null) && vehicleType.toLowerCase().contains("bus");

        // SUMO's reported position is typically near the *front* of the vehicle; we draw centered.
        // Shift the vehicle back by half its screen length so vehicles don't visually cross stop lines.
        if (isBus) {
            // Draw bus - longer than a car
            double halfLenPx = busLengthPxForZoom(view, scale) * 0.5;
            drawBusShapeWithVectors(view, g, x - fx * halfLenPx, y - fy * halfLenPx, fx, fy, rx, ry, color, scale);
        } else {
            // Draw regular car
            double halfLenPx = carLengthPxForZoom(view, scale) * 0.5;
            drawCarShapeWithVectors(view, g, x - fx * halfLenPx, y - fy * halfLenPx, fx, fy, rx, ry, color, scale);
        }
    }

    private static double carSizeMulForZoom(MapView view, double mapScale) {
        // This function controls how car size changes with zoom.
        // Small when zoomed out, grows smoothly when zooming in.
        double zoom = view.clamp(mapScale, 1.0, 25.0);
        double t = view.clamp((zoom - 1.0) / (10.0 - 1.0), 0.0, 1.0);
        t = t * t * (3.0 - 2.0 * t); // smoothstep
        return view.clamp(0.75 + 1.35 * t, 0.7, 2.2);
    }

    private static double carLengthPxForZoom(MapView view, double mapScale) {
        return Math.max(8.5, 12.0 * carSizeMulForZoom(view, mapScale));
    }

    private static double busLengthPxForZoom(MapView view, double mapScale) {
        // Slightly longer: ~1.8x car length at typical zooms.
        return Math.max(13.0, 22.0 * carSizeMulForZoom(view, mapScale));
    }

    private static double busWidthPxForZoom(MapView view, double mapScale) {
        double length = busLengthPxForZoom(view, mapScale);
        return length * 0.32; // Slightly wider ratio for better proportions
    }

    private static void drawCarShapeWithVectors(MapView view, GraphicsContext g, double cx, double cy,
                                                double fx, double fy, double rx, double ry,
                                                Color color, double mapScale) {
        // Car dimensions in screen pixels.
        // We intentionally scale with the user's zoom (not world scale) so cars stay readable.
        double length = carLengthPxForZoom(view, mapScale);
        double width = length * 0.45;
        double halfLen = length / 2.0;
        double halfWid = width / 2.0;

        // Calculate the 4 corners of the car body
        // Front-right, Front-left, Back-left, Back-right
        double[] xPoints = new double[4];
        double[] yPoints = new double[4];

        // Front-right corner
        xPoints[0] = cx + fx * halfLen + rx * halfWid;
        yPoints[0] = cy + fy * halfLen + ry * halfWid;
        // Front-left corner
        xPoints[1] = cx + fx * halfLen - rx * halfWid;
        yPoints[1] = cy + fy * halfLen - ry * halfWid;
        // Back-left corner
        xPoints[2] = cx - fx * halfLen - rx * halfWid;
        yPoints[2] = cy - fy * halfLen - ry * halfWid;
        // Back-right corner
        xPoints[3] = cx - fx * halfLen + rx * halfWid;
        yPoints[3] = cy - fy * halfLen + ry * halfWid;

        // Draw car body outline for visibility
        g.setStroke(Color.WHITE);
        g.setLineWidth(1.0);
        g.strokePolygon(xPoints, yPoints, 4);

        // Draw car body fill
        g.setFill(color);
        g.fillPolygon(xPoints, yPoints, 4);

        // Draw windshield (front window) - darker shade
        Color windowColor = color.darker().darker();
        double windowLen = length * 0.2;
        double windowWid = width * 0.6;
        double windowHalfWid = windowWid / 2.0;
        // Window position: slightly behind the front
        double windowOffset = halfLen - windowLen * 0.8;

        double[] wxPoints = new double[4];
        double[] wyPoints = new double[4];
        wxPoints[0] = cx + fx * windowOffset + rx * windowHalfWid;
        wyPoints[0] = cy + fy * windowOffset + ry * windowHalfWid;
        wxPoints[1] = cx + fx * windowOffset - rx * windowHalfWid;
        wyPoints[1] = cy + fy * windowOffset - ry * windowHalfWid;
        wxPoints[2] = cx + fx * (windowOffset - windowLen) - rx * windowHalfWid;
        wyPoints[2] = cy + fy * (windowOffset - windowLen) - ry * windowHalfWid;
        wxPoints[3] = cx + fx * (windowOffset - windowLen) + rx * windowHalfWid;
        wyPoints[3] = cy + fy * (windowOffset - windowLen) + ry * windowHalfWid;

        g.setFill(windowColor);
        g.fillPolygon(wxPoints, wyPoints, 4);

        // Draw headlights (small circles at front corners)
        g.setFill(Color.YELLOW);
        double lightSize = width * 0.22;
        double lightOffset = halfLen - lightSize * 0.5;
        double lightSide = halfWid - lightSize * 0.3;

        // Right headlight
        double lx1 = cx + fx * lightOffset + rx * lightSide;
        double ly1 = cy + fy * lightOffset + ry * lightSide;
        g.fillOval(lx1 - lightSize / 2, ly1 - lightSize / 2, lightSize, lightSize);

        // Left headlight
        double lx2 = cx + fx * lightOffset - rx * lightSide;
        double ly2 = cy + fy * lightOffset - ry * lightSide;
        g.fillOval(lx2 - lightSize / 2, ly2 - lightSize / 2, lightSize, lightSize);
    }

    private static void drawBusShapeWithVectors(MapView view, GraphicsContext g, double cx, double cy,
                                                double fx, double fy, double rx, double ry,
                                                Color color, double mapScale) {
        // Bus dimensions in screen pixels - buses are longer than cars
        double length = busLengthPxForZoom(view, mapScale);
        double width = busWidthPxForZoom(view, mapScale);
        double halfLen = length / 2.0;
        double halfWid = width / 2.0;

        // Calculate the 4 corners of the bus body
        double[] xPoints = new double[4];
        double[] yPoints = new double[4];

        // Front-right corner
        xPoints[0] = cx + fx * halfLen + rx * halfWid;
        yPoints[0] = cy + fy * halfLen + ry * halfWid;
        // Front-left corner
        xPoints[1] = cx + fx * halfLen - rx * halfWid;
        yPoints[1] = cy + fy * halfLen - ry * halfWid;
        // Back-left corner
        xPoints[2] = cx - fx * halfLen - rx * halfWid;
        yPoints[2] = cy - fy * halfLen - ry * halfWid;
        // Back-right corner
        xPoints[3] = cx - fx * halfLen + rx * halfWid;
        yPoints[3] = cy - fy * halfLen + ry * halfWid;

        // Draw bus body outline for visibility
        g.setStroke(Color.WHITE);
        g.setLineWidth(1.2);
        g.setLineDashes();
        g.strokePolygon(xPoints, yPoints, 4);

        // Draw bus body fill
        g.setFill(color);
        g.fillPolygon(xPoints, yPoints, 4);

        // Draw multiple windows along the bus (characteristic bus look)
        Color windowColor = color.darker().darker();
        g.setFill(windowColor);

        double windowLen = length * 0.08;
        double windowWid = width * 0.55;
        double windowHalfWid = windowWid / 2.0;

        // Draw 4 windows evenly spaced along the bus
        int numWindows = 4;
        double windowSpacing = length * 0.18;
        double firstWindowOffset = halfLen - windowLen * 1.2;

        for (int w = 0; w < numWindows; w++) {
            double windowOffset = firstWindowOffset - w * windowSpacing;

            double[] wxPoints = new double[4];
            double[] wyPoints = new double[4];
            wxPoints[0] = cx + fx * windowOffset + rx * windowHalfWid;
            wyPoints[0] = cy + fy * windowOffset + ry * windowHalfWid;
            wxPoints[1] = cx + fx * windowOffset - rx * windowHalfWid;
            wyPoints[1] = cy + fy * windowOffset - ry * windowHalfWid;
            wxPoints[2] = cx + fx * (windowOffset - windowLen) - rx * windowHalfWid;
            wyPoints[2] = cy + fy * (windowOffset - windowLen) - ry * windowHalfWid;
            wxPoints[3] = cx + fx * (windowOffset - windowLen) + rx * windowHalfWid;
            wyPoints[3] = cy + fy * (windowOffset - windowLen) + ry * windowHalfWid;

            g.fillPolygon(wxPoints, wyPoints, 4);
        }

        // Draw headlights (small circles at front corners)
        g.setFill(Color.YELLOW);
        double lightSize = width * 0.25;
        double lightOffset = halfLen - lightSize * 0.4;
        double lightSide = halfWid - lightSize * 0.2;

        // Right headlight
        double lx1 = cx + fx * lightOffset + rx * lightSide;
        double ly1 = cy + fy * lightOffset + ry * lightSide;
        g.fillOval(lx1 - lightSize / 2, ly1 - lightSize / 2, lightSize, lightSize);

        // Left headlight
        double lx2 = cx + fx * lightOffset - rx * lightSide;
        double ly2 = cy + fy * lightOffset - ry * lightSide;
        g.fillOval(lx2 - lightSize / 2, ly2 - lightSize / 2, lightSize, lightSize);

        // Draw tail lights (red at back)
        g.setFill(Color.RED);
        double tailLightOffset = -halfLen + lightSize * 0.4;

        // Right tail light
        double tx1 = cx + fx * tailLightOffset + rx * lightSide;
        double ty1 = cy + fy * tailLightOffset + ry * lightSide;
        g.fillOval(tx1 - lightSize / 2, ty1 - lightSize / 2, lightSize, lightSize);

        // Left tail light
        double tx2 = cx + fx * tailLightOffset - rx * lightSide;
        double ty2 = cy + fy * tailLightOffset - ry * lightSide;
        g.fillOval(tx2 - lightSize / 2, ty2 - lightSize / 2, lightSize, lightSize);
    }

    private static Point2D transform(MapView view, Point2D p, double height, double scale) {
        double x = view.padding + (p.getX() - view.minX) * scale + view.offsetX;
        double y = view.padding + (view.maxY - p.getY()) * scale + view.offsetY; // flip Y for screen coords
        return new Point2D(x, y);
    }

    private static void strokePolyline(MapView view, GraphicsContext g, List<Point2D> worldPolyline, double height, double scale,
                                       double width, Color stroke, double[] dashes, StrokeLineCap cap) {
        if (worldPolyline == null || worldPolyline.size() < 2) return;
        g.save();
        g.setLineWidth(width);
        g.setStroke(stroke);
        // Be explicit: JavaFX can retain the previous dash state if we pass null,
        // which makes "solid" lines (like the 2-way divider) appear dashed.
        if (dashes == null) g.setLineDashes();
        else g.setLineDashes(dashes);
        if (cap != null) g.setLineCap(cap);

        Point2D p0 = transform(view, worldPolyline.get(0), height, scale);
        g.beginPath();
        g.moveTo(p0.getX(), p0.getY());
        for (int i = 1; i < worldPolyline.size(); i++) {
            Point2D pi = transform(view, worldPolyline.get(i), height, scale);
            g.lineTo(pi.getX(), pi.getY());
        }
        g.stroke();
        g.restore();
    }

    private static void drawLaneSingleEdge(MapView view, GraphicsContext g, List<Point2D> worldPolyline, double height, double scale,
                                           double laneScreenWidth, Color stroke, double lineWidth, int sideSign) {
        drawLaneSingleEdge(view, g, worldPolyline, height, scale, laneScreenWidth, stroke, lineWidth, sideSign, null);
    }

    private static void drawLaneSingleEdge(MapView view, GraphicsContext g, List<Point2D> worldPolyline, double height, double scale,
                                           double laneScreenWidth, Color stroke, double lineWidth, int sideSign,
                                           double[] dashes) {
        if (worldPolyline == null || worldPolyline.size() < 2) return;
        g.save();
        g.setStroke(stroke);
        if (dashes == null) g.setLineDashes();
        else g.setLineDashes(dashes);
        g.setLineWidth(lineWidth);
        // Ensure square end-caps for borders/separators too.
        g.setLineCap(StrokeLineCap.BUTT);

        // IMPORTANT:
        // If we draw dashed lines segment-by-segment (strokeLine per segment), JavaFX resets the
        // dash phase each segment. With short segments (common in SUMO polylines), the result can
        // look like a solid line or like dashes are missing.
        // For dashed separators, draw a single continuous path.
        if (dashes != null) {
            List<Point2D> pts = buildOffsetPolylineScreen(view, worldPolyline, height, scale, laneScreenWidth, sideSign);
            if (pts.size() >= 2) {
                Point2D p0 = pts.get(0);
                g.beginPath();
                g.moveTo(p0.getX(), p0.getY());
                for (int i = 1; i < pts.size(); i++) {
                    Point2D pi = pts.get(i);
                    g.lineTo(pi.getX(), pi.getY());
                }
                g.stroke();
                g.restore();
                return;
            }
        }

        int sign = (sideSign >= 0) ? 1 : -1;
        for (int i = 1; i < worldPolyline.size(); i++) {
            Point2D w1 = worldPolyline.get(i - 1);
            Point2D w2 = worldPolyline.get(i);
            Point2D p1 = transform(view, w1, height, scale);
            Point2D p2 = transform(view, w2, height, scale);
            double dx = p2.getX() - p1.getX();
            double dy = p2.getY() - p1.getY();
            double len = Math.hypot(dx, dy);
            if (len < 0.0001) continue;
            double nx = -dy / len;
            double ny = dx / len;

            double offset = Math.max(1.8, laneScreenWidth / 2.0);
            double ox = nx * offset * sign;
            double oy = ny * offset * sign;
            g.strokeLine(p1.getX() + ox, p1.getY() + oy,
                    p2.getX() + ox, p2.getY() + oy);
        }

        g.restore();
    }

    private static List<Point2D> buildOffsetPolylineScreen(MapView view, List<Point2D> worldPolyline, double height, double scale,
                                                           double laneScreenWidth, int sideSign) {
        List<Point2D> out = new ArrayList<>();
        if (worldPolyline == null || worldPolyline.size() < 2) return out;

        int sign = (sideSign >= 0) ? 1 : -1;
        double offset = Math.max(1.8, laneScreenWidth / 2.0) * sign;

        // Transform all points once.
        int n = worldPolyline.size();
        Point2D[] p = new Point2D[n];
        for (int i = 0; i < n; i++) {
            p[i] = transform(view, worldPolyline.get(i), height, scale);
        }

        // Compute normals per segment in screen space.
        double[] nx = new double[n - 1];
        double[] ny = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            double dx = p[i + 1].getX() - p[i].getX();
            double dy = p[i + 1].getY() - p[i].getY();
            double len = Math.hypot(dx, dy);
            if (len < 1e-6) {
                nx[i] = 0;
                ny[i] = 0;
            } else {
                nx[i] = -dy / len;
                ny[i] = dx / len;
            }
        }

        // Offset each vertex by the averaged adjacent normals.
        for (int i = 0; i < n; i++) {
            double ax, ay;
            if (i == 0) {
                ax = nx[0];
                ay = ny[0];
            } else if (i == n - 1) {
                ax = nx[n - 2];
                ay = ny[n - 2];
            } else {
                ax = nx[i - 1] + nx[i];
                ay = ny[i - 1] + ny[i];
            }
            double alen = Math.hypot(ax, ay);
            if (alen < 1e-6) {
                // Fallback to any available normal.
                if (i > 0) {
                    ax = nx[i - 1];
                    ay = ny[i - 1];
                    alen = Math.hypot(ax, ay);
                }
                if (alen < 1e-6 && i < n - 1) {
                    ax = nx[i];
                    ay = ny[i];
                    alen = Math.hypot(ax, ay);
                }
            }
            if (alen < 1e-6) {
                out.add(p[i]);
            } else {
                ax /= alen;
                ay /= alen;
                out.add(new Point2D(p[i].getX() + ax * offset, p[i].getY() + ay * offset));
            }
        }

        return out;
    }

    private static void drawRoadDirectionArrows(MapView view, GraphicsContext g, double height, double scale, Color stroke) {
        // Group lanes by edgeId so arrows are drawn once per road direction.
        Map<String, List<LaneShape>> byEdge = new HashMap<>();
        for (LaneShape lane : view.lanes) {
            if (lane == null || lane.edgeId == null || lane.edgeId.isEmpty()) continue;
            // Skip internal lanes (SUMO usually prefixes with ':')
            if (lane.edgeId.startsWith(":")) continue;
            byEdge.computeIfAbsent(lane.edgeId, k -> new ArrayList<>()).add(lane);
        }
        if (byEdge.isEmpty()) return;

        g.setStroke(stroke);
        g.setLineDashes();
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);

        for (List<LaneShape> group : byEdge.values()) {
            if (group == null || group.isEmpty()) continue;
            group.sort(Comparator.comparingInt(ls -> (ls != null) ? ls.laneIndex : Integer.MAX_VALUE));
            LaneShape rep = group.get(group.size() / 2);
            if (rep == null || rep.polyline == null || rep.polyline.size() < 2) continue;

            // Estimate road width from number of lanes.
            double lanesCount = group.size();
            double laneScreenWidth = Math.max(1.5, rep.widthMeters * scale);
            double roadScreenWidth = Math.max(laneScreenWidth, laneScreenWidth * lanesCount);
            if (roadScreenWidth < 6.0) continue;

            drawSlimArrowsOnPolyline(view, g, rep.polyline, height, scale, roadScreenWidth);
        }
    }

    private static void drawSlimArrowsOnPolyline(MapView view, GraphicsContext g, List<Point2D> worldPolyline, double height, double scale,
                                                 double roadScreenWidth) {
        if (worldPolyline == null || worldPolyline.size() < 2) return;
        // Arrow cadence in screen pixels.
        double spacing = Math.max(75.0, roadScreenWidth * 2.6);
        double shaftLen = Math.max(10.0, Math.min(22.0, roadScreenWidth * 0.45));
        double headLen = Math.max(4.0, shaftLen * 0.38);
        double headAngle = Math.toRadians(28);
        g.setLineWidth(Math.max(1.1, Math.min(1.8, roadScreenWidth * 0.06)));

        double traveled = 0.0;
        double nextAt = spacing;

        for (int i = 1; i < worldPolyline.size(); i++) {
            Point2D p1 = transform(view, worldPolyline.get(i - 1), height, scale);
            Point2D p2 = transform(view, worldPolyline.get(i), height, scale);
            double dx = p2.getX() - p1.getX();
            double dy = p2.getY() - p1.getY();
            double segLen = Math.hypot(dx, dy);
            if (segLen < 0.0001) continue;
            double ux = dx / segLen;
            double uy = dy / segLen;

            while (traveled + segLen >= nextAt) {
                double t = (nextAt - traveled) / segLen;
                double cx = p1.getX() + dx * t;
                double cy = p1.getY() + dy * t;

                double baseX = cx;
                double baseY = cy;
                double tipX = cx + ux * shaftLen;
                double tipY = cy + uy * shaftLen;

                // shaft
                g.strokeLine(baseX, baseY, tipX, tipY);

                // head: two short lines rotated from direction
                double cos = Math.cos(headAngle);
                double sin = Math.sin(headAngle);
                // rotate (-u) by +/- angle
                double bx = -ux;
                double by = -uy;
                double lx = bx * cos - by * sin;
                double ly = bx * sin + by * cos;
                double rx = bx * cos + by * sin;
                double ry = -bx * sin + by * cos;
                g.strokeLine(tipX, tipY, tipX + lx * headLen, tipY + ly * headLen);
                g.strokeLine(tipX, tipY, tipX + rx * headLen, tipY + ry * headLen);

                nextAt += spacing;
            }
            traveled += segLen;
        }
    }

    private static List<Point2D> buildAveragedCenterline(List<Point2D> a, List<Point2D> b, int samples) {
        List<Point2D> out = new ArrayList<>();
        if (a == null || b == null || a.size() < 2 || b.size() < 2) return out;
        int n = Math.max(2, samples);
        for (int i = 0; i < n; i++) {
            double t = (n == 1) ? 0.0 : ((double) i / (double) (n - 1));
            Point2D pa = MapViewGeometry.pointAlongPolyline(a, t);
            Point2D pb = MapViewGeometry.pointAlongPolyline(b, t);
            if (pa == null || pb == null) continue;
            out.add(new Point2D((pa.getX() + pb.getX()) * 0.5, (pa.getY() + pb.getY()) * 0.5));
        }
        return out;
    }

    private static double polylineLength(List<Point2D> poly) {
        if (poly == null || poly.size() < 2) return 0.0;
        double sum = 0.0;
        for (int i = 1; i < poly.size(); i++) {
            sum += poly.get(i).distance(poly.get(i - 1));
        }
        return sum;
    }

    private static double averagePolylineDistanceMid(List<Point2D> a, List<Point2D> b, int samples) {
        if (a == null || b == null || a.size() < 2 || b.size() < 2) return Double.POSITIVE_INFINITY;
        int n = Math.max(4, samples);
        double sum = 0.0;
        int used = 0;
        // Ignore the ends (junction curvature) and compare the mid-section.
        double t0 = 0.15;
        double t1 = 0.85;
        for (int i = 0; i < n; i++) {
            double u = (double) i / (double) (n - 1);
            double t = t0 + (t1 - t0) * u;
            Point2D pa = MapViewGeometry.pointAlongPolyline(a, t);
            Point2D pb = MapViewGeometry.pointAlongPolyline(b, t);
            if (pa == null || pb == null) continue;
            sum += pa.distance(pb);
            used++;
        }
        if (used == 0) return Double.POSITIVE_INFINITY;
        return sum / (double) used;
    }

    private static Point2D polylineDirectionMid(List<Point2D> poly) {
        if (poly == null || poly.size() < 2) return new Point2D(0, 0);
        Point2D a = MapViewGeometry.pointAlongPolyline(poly, 0.20);
        Point2D b = MapViewGeometry.pointAlongPolyline(poly, 0.80);
        if (a == null || b == null) return polylineDirection(poly);
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) return new Point2D(0, 0);
        return new Point2D(dx / len, dy / len);
    }

    private static Point2D polylineDirection(List<Point2D> poly) {
        if (poly == null || poly.size() < 2) return new Point2D(0, 0);
        Point2D a = poly.get(0);
        Point2D b = poly.get(poly.size() - 1);
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) return new Point2D(0, 0);
        return new Point2D(dx / len, dy / len);
    }
}
