# Real-Time Traffic Simulation with Java User Guide

## Introduction

This application is a real-time traffic simulation application using Java and other support tools. Working development and documentation of the project can be found in our GitHub repository:

<!-- [link_format](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop) -->

https://github.com/hunggiadao/realtime-traffic-simulation-java-oop

<!-- **Note:** this project requires Git Large File Storage (LFS) tracking for transfering files larger than 50MB. -->

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

## Application Usage

Please refer to *Figure 1*.

### Loading SumoConfig files

In the ***Simulation*** tab, type in the path for a `.sumocfg` file or click ***Browse*** to find it in a File Explorer popup menu. Click ***Open*** to load the SumoConfig file into the application.

### Running a SUMO simulation

Click the ***Connect*** button in the top row to initialize a connection. After which, you can either choose ***Start*** or ***Step*** to advance through the simulation.

- ***Start*** runs the simulation autonomously until the end. During which, you can click ***Pause*** to pause at any point.
- ***Step*** advances by a single time step, allowing the user to inspect and monitor minute changes over time.

Users can also adjust the speed using the ***Speed*** slider, allowing them to speed up or slow down the simulation.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_stress.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 2: Running Simulation Example
	</p>
</div>

### Vehicle Injection

In the ***Vehicles*** tab, users can add new vehicles to the map with options for which edge, vehicle color, how many vehicles to add, and maximum speed.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_stress_vehicle_inject.png" alt="Image 1" style="height: 300px; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 3: Vehicle Injection Menu
	</p>
</div>

### Filtering View

In the ***Filters*** tab, users can toggle to show only certain types of vehicles that meet specific criteria.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_stress_filter.png" alt="Image 2" style="height: 300px; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 4: Filter Menu
	</p>
</div>

### Metrics panel

In the ***Charts*** tab of the right panel, users can inspect different metrics for the running simulation.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_charts.png" alt="Image 2" style="height: 300px; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 5: Charts Menu
	</p>
</div>

In the ***Vehicle Table*** tab, users can inspect all metadata and metrics of all vehicle instances in the simulation in tabular form.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_stress_vehicle_table.png" alt="Image 2" style="height: 300px; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 6: Vehicle Table Menu
	</p>
</div>

### Exporting results

To be added

## Notes

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
