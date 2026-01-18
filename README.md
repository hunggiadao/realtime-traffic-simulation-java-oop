# Real-Time Traffic Simulation with Java (an OOP Project)

## Introduction

This project is the development of a real-time traffic simulation application using Java as the main programming language and its various libraries, such as JavaFX, TraCI as a Service (TraaS), etc. It also utilizes functionalities of the Simulation of Urban Mobility (SUMO) software package to visualize traffic network and traffic flows. The application is designed to provide some basic mobility control functionalities and allows for user’s interactive control, enabling its use for teaching, learning, and researching urban mobility.

Working development and documentation of the project can be found in our GitHub repository:

<!-- [link_format](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop) -->

https://github.com/hunggiadao/realtime-traffic-simulation-java-oop

The project utilizes the following tools:
- **Eclipse IDE**: free, open-source Integrated Development Environment used for developing and testing applications in Java
- **Visual Studio Code**: free, open-source code editor for general coding and editing of different text formats
- **Simulation of Urban Mobility (SUMO)**: free, open-source, microscopic, multimodal traffic simulation Java package designed to handle road networks and various types of transportation, including cars, buses, bicycles, planes, and pedestrians (includes **netedit** for network creation and design)
- **JavaFX**: software platform used to create graphical interfaces for desktop and internet applications
- **TraCI as a Service (TraaS)**: SUMO TraCI API for enabling external access to a running SUMO simulation
- **Dia**: free and open-source diagramming software, for creating UML, class, and use case diagrams
- **LaTeX**: powerful, high-quality document preparation system built on the TeX typesetting program, used to create technical documents, books, articles, mathematical and scientific notation
- **Notion**: all-in-one workspace application for managing knowledge, projects, and tasks
- **Git**: a distributed version control system (VCS) that helps teams build large code projects

<!-- **Note:** this project requires Git Large File Storage (LFS) tracking for transfering files larger than 50MB. -->

## Task Distribution:

|Task|Assignee(s)|
|:------------|:---------------------------|
|Traffic Network Files|Raees Ashraf Shah|
|TraCI Connector + SUMO Integration|Gia Hung Dao|
|Traffic Light Control (wrapper + UI)|Gia Hung Dao <br> Raees Ashraf Shah|
|Hotkey Functionality|Raees Ashraf Shah|
|Vehicle Wrapper + Vehicle Injection|Huu Trung Son Dang <br> Khac Uy Pham <br> Gia Hung Dao|
|GUI Implementation (JavaFX)|Huu Trung Son Dang|
|Infrastructure Wrapper Class|Huy Hoang Bui <br> Gia Hung Dao|
|Logger + Run Scripts|Huu Trung Son Dang|
|Exception handler|Huu Trung Son Dang|
|CSV and PDF Exporter|Raees Ashraf Shah <br> Gia Hung Dao|
|Architecture and UML Diagrams|Khac Uy Pham <br> Huu Trung Son Dang|

<!-- ![alt text](https://github.com/hunggiadao/human_age_detection/blob/main/Presentation/data_collection.png) -->

## Setting up and Running

Users can either download the latest source folder in [Releases](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/releases) or clone the repository directly using the following command:
```
$ git clone git@github.com:hunggiadao/realtime-traffic-simulation-java-oop.git
```

<!-- Install and use Git LFS:
Download and install [Git LFS](https://git-lfs.com).
Type the following command into terminal to enable LFS tracking in your current Git repository:
```
$ git lfs install
``` -->

<!-- The provided `.gitattributes` should have all necessary LFS file types for this project. If you wish to include more file types for LFS tracking, you can add them directly to `.gitattributes` using the same syntax, or type this command into terminal:
```
$ git lfs track "*.<file-type>"
``` -->

The provided `.gitignore` includes all telemetry and miscellaneous types that are otherwise unnecessary for storing, such as `**Zone.Identifier` or `*.log`.

`*` means all files of this type.

Once the download has finished, simply double-click the `run.bat` script file inside the `java-code/` folder to start the application.

## Application Features

### Graphical User Interface (GUI)

The interface is built around a `BorderPane` layout, dividing the window into clear functional regions.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_final.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 1: Main UI when first loaded
	</p>
</div>

<!-- ![alt text](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_milestone_1.png) -->

At the top of the application, a responsive toolbar provides the essential simulation controls, including opening a SUMO configuration file, connecting to the simulation backend, starting or pausing the simulation, executing single simulation steps, and adjusting the simulation speed. This layout remains clean and stable when the window is resized.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/map_final.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 2: Main UI after SUMO connection
	</p>
</div>

A much more detailed description of Application Features can be found in Chapter 3 of our [PDF report document](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/report/report.pdf) or at the following link:

https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/report/report.pdf

<!-- ![alt text](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_milestone_2.png) -->

### Architecture Diagram

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/architecture_diagram.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 3: Architecture Diagram
	</p>
</div>

<!-- ![alt text](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/architecture_diagram.png) -->

### Class Diagrams

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/Class_Overview.jpg" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 4: Class Diagram Overview
	</p>
</div>

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/Class_TraCI.jpg" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 5: TraCI Class Diagram
	</p>
</div>

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/Class_UI.jpg" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 6: UI Class Diagram
	</p>
</div>

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/Class_SimulationRuntime.jpg" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 7: Simulator Class Diagram
	</p>
</div>

### Use Case Diagram

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/Usecase_TrafficSimulationApplication.jpg" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 8: Use Case Diagram
	</p>
</div>

<!-- ![alt text](<to be added>) -->

## Milestone 1

### 1.1. Purpose
Lay the foundation for the project by designing the architecture, planning the components, and demonstrating basic SUMO integration. 

### 1.2. Deliverables
**Technical Components:**

✅ Project Overview (1–2 pages) <br>
✅ Architecture Diagram <br>
✅ Class Design for TraaS wrapper (Vehicle, TrafficLight, etc.) <br>
✅ GUI Mockups (map view, control panels, dashboard) <br>
✅ SUMO Connection Demo (list traffic lights, step simulation) <br>
✅ Technology Stack Summary

### 1.3. Software Engineering Practices
✅ Git Repository Setup (README, initial commit) <br>
✅ Time plan: Features → Time <br>
✅ Team Roles

## Milestone 2

### 2.1. Purpose 
Deliver a working version of the system with core features implemented and demonstrate progress toward the final product.

### 2.2. Deliverables
**Technical Components:**

✅ Working Application:
- Live SUMO connection
- Vehicle injection
- Traffic light control
- Map visualization

✅ Code Documentation (Javadoc or inline comments) <br>
✅ User Guide Draft <br>
✅ Test Scenario (at least one stress test) <br>
✅ Progress Summary (status, challenges) 

### 2.3. Software Engineering Practices 
✅ Git Commit History (clear messages, feature branches) <br>
✅ Revisiting: team roles

## Final Milestone

### 3.1. Purpose 
Showcase the completed system, demonstrate its features, and reflect on the development process.

### 3.2. Deliverables 
✅ Final Application:
- Full GUI with map, controls, statistics
- Vehicle grouping/filtering
- Traffic light adaptation (manual or rule-based)
- Exportable reports (CSV and/or PDF)

✅ Final Documentation:
- Updated user guide
- Final code documentation
- Summary of enhancements and design decisions

✅ Presentation:
- Live demo
- Architecture and feature explanation
- Performance evaluation
- Team reflection

✅ Final Retrospective <br>
✅ Clean Git Repository <br>
✅ Sample Exported Reports

## Testing

**Stress test setup:**

As of Milestone 2, we have implemented a stress test SUMO config: [Stress.sumocfg](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/SumoConfig/Stress.sumocfg) to test our vehicle injection and filtering logic. This facilitates a heavy simulation load with hundreds of vehicles present at once.

**Monitored metrics:**

<div style="display: flex; gap: 10px;">
	<div style="flex: 1 1 0; max-width: 100%;">
		<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/vehicle_injection.png" alt="Image 1" style="height: 300px; width: auto; max-width: 100%;">
		<p style="text-align: center; font-style: italic; font-size: 1em;">
			Figure 9: Vehicle Injection Menu
		</p>
	</div>
	<div style="flex: 1 1 0; max-width: 100%;">
		<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/traffic_light_tab.png" alt="Image 2" style="height: 300px; width: auto; max-width: 100%;">
		<p style="text-align: center; font-style: italic; font-size: 1em;">
			Figure 10: Traffic Lights Menu
		</p>
	</div>
	<div style="flex: 1 1 0; max-width: 100%;">
		<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/ui_filter.png" alt="Image 3" style="height: 300px; width: auto; max-width: 100%;">
		<p style="text-align: center; font-style: italic; font-size: 1em;">
			Figure 11: Filter Menu
		</p>
	</div>
	<div style="flex: 1 1 0; max-width: 100%;">
		<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/map_overview.png" alt="Image 4" style="height: 300px; width: auto; max-width: 100%;">
		<p style="text-align: center; font-style: italic; font-size: 1em;">
			Figure 12: Charts Menu
		</p>
	</div>
	<div style="flex: 1 1 0; max-width: 100%;">
		<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/vehicles_data_table.png" alt="Image 5" style="height: 300px; width: auto; max-width: 100%;">
		<p style="text-align: center; font-style: italic; font-size: 1em;">
			Figure 13: Vehicle Data Menu
		</p>
	</div>
</div>

**Running stress test:**

Below is a snapshot of the running stress test with about 75 vehicles present.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_stress_2.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 14: Running Stress Test
	</p>
</div>

<!-- ![alt text](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_stress.png) -->

<!-- ### Model design: -->

<!-- 1. Feature extraction:
-- Load the pretrained weights on IMDB (using `model_state_dict`) into our Resnet50 backbone layer.
2. Shared Fully Connected Layers:
-- 1024 → 512 neurons: Gradual reduction of feature space for efficient representation.
-- Regularization: Batch normalization and 40% dropout to prevent overfitting.
-- Non-linearity: ReLU activation enables learning complex relationships.
3. Age Prediction Head:
-- 512 → 128 → 1 neurons: Focuses on refining features for accurate regression output.
-- Final Output: Single continuous value for age prediction.
4. Freeze Layers until layer2.0.conv1:
-- Purpose: Gradually unfreezes layers to fine-tune specific parts of the model.
-- Mechanism: Freezes parameters until freeze_until layer is reached, then allows training.
-- Benefit: Preserves pre-trained knowledge while adapting to new tasks. -->

### Evaluation:

For a performant application, we relied primarily on the built-in SUMO libraries, which provide efficient and easy to use value retrievals and setting methods. We also eliminated most performance issues encountered in Milestone 2 by employing lazy updates or caching of commonly used values. Overall, our application is significantly more responsive than before. As a result, this allowed for more fine-tuned rendering and a smaller `step-length` for smooth animation.

To address the bottlenecks identified during stress testing, we have:
- **Optimized map rendering**: Implement spatial partitioning (e.g., QuadTree) to only render vehicles and map elements currently within the viewport.
- **Separated the threads between map simulation and UI rendering**: to maintain simulation as fast as possible, and only update the UI when needed.
- **Throttled UI updates**: Decouple the data fetch rate from the render rate to ensure the UI remains responsive even when the simulation is running at high speeds.

<!-- ![alt text]() -->

## Conclusion

By the final milestone, we have delivered a fully functional realtime traffic simulation application. The system can connect to a live SUMO simulation via TraCI, run or step the simulation, visualize the network and moving vehicles directly in the JavaFX GUI, and export simulation data for further inspection and fine tuning. Core interactive features are implemented, enabling users to actively influence traffic flow during runtime.

We have also addressed performance challenges presented during Milestone 2. The main challenges identified are per-formance and update-frequency trade-offs when handling large vehicle counts, particularly during stress testing. These findings guide the next development steps: improving rendering efficiency, reducing unnecessary UI refresh work, and extending the control logic. By employing lazy updates and utilizing as many dedicated SUMO function calls as possible to reduce computing overhead, we achieved better running performance and less lag time than we had had in Milestone 2.

The final application will include comprehensive reporting tools:

- **Data Export**: Functionality to export simulation statistics (travel times, congestion levels) to CSV or PDF formats.
- **Enhanced Dashboard**: A more detailed statistics view including average waiting times and other estimates.

<!-- ## Reference -->

<!-- Jakub Paplhám and Franc, V. (2024).
*A Call to Reflect on Evaluation Practices for Age Estimation: Comparative Analysis of the State-of-the-Art and a Unified Benchmark.*
2022 IEEE/CVF Conference on Computer Vision and Pattern Recognition (CVPR), pp.1196–1205. doi:https://doi.org/10.1109/cvpr52733.2024.00120. -->

<!-- Sadek, I. (2017).
*Distribution of the world’s population by age and sex, 2017.*
Source: United Nations, Department of Economic and Social Affairs, Population Division (2017). World Population Prospects: The 2017 Revision. New York: United Nations. -->

<!-- ‌Rajput, S. (2020).
*Face Detection using MTCNN.*\
Medium: https://medium.com/@saranshrajput/face-detection-using-mtcnn-f3948e5d1acb. -->
