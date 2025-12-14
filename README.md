# Real-Time Traffic Simulation with Java (an OOP Project)

## Introduction

This project is the development of a real-time traffic simulation application using Java and other support tools, such as JavaFX and the Simulation of Urban Mobility (SUMO) traffic simulator. During Milestone 1, the primary objective was to design the graphical user interface that will serve as the foundation for future simulation control and visualization.

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

<!-- **Note:** this project requires Git Large File Storage (LFS) tracking for transfering files larger than 50MB. -->

## Task Distribution:

|Task|Assignee(s)|
|:------------|:---------------------------|
|Traffic Network Files|Raees Ashraf Shah|
|TraCI Connector|Gia Hung Dao|
|Logger|Huu Trung Son Dang|
|Traffic Light Wrapper Class|Gia Hung Dao <br> Raees Ashraf Shah|
|GUI|Huu Trung Son Dang|
|Infrastructure Wrapper Class|Huy Hoang Bui|
|Vehicle Wrapper Class|Khac Uy Pham <br> Gia Hung Dao|
|Architecture and UML Diagrams|Khac Uy Pham|
|Run scripts|Huu Trung Son Dang|

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

Once the download has finished, simply double-click the `run.bat` script file inside the `java-code/` folder to start the application. Once the full application's GUI is loaded, it looks as follows:

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_milestone_1.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 1: Main UI when first loaded
	</p>
</div>

## Application Features

### Graphical User Interface (GUI)

The interface is built around a `BorderPane` layout, dividing the window into clear functional regions.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_milestone_1.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 1: Main UI when first loaded
	</p>
</div>

<!-- ![alt text](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_milestone_1.png) -->

At the top of the application, a responsive toolbar provides the essential simulation controls, including opening a SUMO configuration file, connecting to the simulation backend, starting or pausing the simulation, executing single simulation steps, and adjusting the simulation speed. This layout remains clean and stable when the window is resized.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_milestone_2.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 2: Main UI after SUMO connection
	</p>
</div>

<!-- ![alt text](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_milestone_2.png) -->

### Architecture Diagram

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/architecture_diagram.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 3: Architecture Diagram
	</p>
</div>

<!-- ![alt text](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/architecture_diagram.png) -->

### Use Case Diagram

To be added

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
✅ SUMO Connection Demo (list traffic lights, step simulation) • Technology Stack Summary

### 1.3. Software Engineering Practices
✅ Git Repository Setup (README, initial commit) <br>
✅ Note: More information will be announced <br>
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

## Final Submission

To be added

## Testing

**Stress test setup:**

As of Milestone 2, we have implemented a stress test SUMO config: [Stress.sumocfg](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/SumoConfig/Stress.sumocfg) to test our vehicle injection and filtering logic. This facilitates a heavy simulation load with hundreds of vehicles present at once.

**Monitored metrics:**

<div style="display: flex; gap: 10px;">
	<div style="flex: 1 1 0; max-width: 100%;">
		<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_stress_vehicle_inject.png" alt="Image 1" style="height: 300px; width: auto; max-width: 100%;">
		<p style="text-align: center; font-style: italic; font-size: 1em;">
			Figure 4: Vehicle Injection Menu
		</p>
	</div>
	<div style="flex: 1 1 0; max-width: 100%;">
		<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_stress_filter.png" alt="Image 2" style="height: 300px; width: auto; max-width: 100%;">
		<p style="text-align: center; font-style: italic; font-size: 1em;">
			Figure 5: Filter Menu
		</p>
	</div>
	<div style="flex: 1 1 0; max-width: 100%;">
		<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_stress_vehicle_table.png" alt="Image 3" style="height: 300px; width: auto; max-width: 100%;">
		<p style="text-align: center; font-style: italic; font-size: 1em;">
			Figure 6: Vehicle Table Menu
		</p>
	</div>
</div>

**Running stress test:**

Below is a snapshot of the running stress test with about 50 vehicles present.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_stress.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 7: Running Stress Test
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

Currently, the application causes the computer to spin up its fan quite a lot when there are more than 50 vehicles on the map.

We can reduce the work load by updating the vehicle table less frequently, or increasing the `stepLength` of the simulation.

<!-- ![alt text]() -->

## Conclusion

To be added

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
