# Real-Time Traffic Simulation with Java User Guide

## Introduction

This application is a real-time traffic simulation application using Java and other support tools. Working development and documentation of the project can be found in our GitHub repository:

<!-- [link_format](https://github.com/hunggiadao/realtime-traffic-simulation-java-oop) -->

https://github.com/hunggiadao/realtime-traffic-simulation-java-oop

<!-- **Note:** this project requires Git Large File Storage (LFS) tracking for transfering files larger than 50MB. -->

## Setting up and Running

Prerequisite requirements:
- JDK 17+

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

Once the download has finished, simply double-click the `run.bat` script file (**Windows** ONLY) inside the `/java-code` folder to start the application.

Users can also use Gradle (both **Linux** and **Windows**) to set up and initiate the program build and run using the following command in the outermost directory:
```
$ gradle clean run
```

Once the full application's GUI is loaded, it looks as follows:

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/main_ui_final.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
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
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/map_final.png" alt="Image 1" style="height: auto; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 2: Running Simulation Example
	</p>
</div>

### Vehicle Injection

In the ***Vehicles*** tab, users can add new vehicles to the map with options for which edge, vehicle color, and how many vehicles to add.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/vehicle_injection.png" alt="Image 1" style="height: 300px; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 3: Vehicle Injection Menu
	</p>
</div>

### Traffic Light Control

In the ***Traffic Lights*** tab, users can select which traffic light to view and control. They can change the current phase of the traffic light, or change the duration of the current phase.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/traffic_light_tab.png" alt="Image 1" style="height: 300px; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 4: Traffic Lights Menu
	</p>
</div>

### Filtering View

In the ***Filters*** tab, users can toggle to show only certain types of vehicles that meet specific criteria. Filtering criteria include speed, color, and congestion heuristics.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/ui_filter.png" alt="Image 2" style="height: 250px; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 5: Filter Menu
	</p>
</div>

### Map interactions with mouse

In the central map view, users can:
- Click and drag with ***LEFT MOUSE BUTTON*** to pan the camera to a different part of the map
- Scroll up and down with ***SCROLL WHEEL*** to zoom in and out of the map

### Metrics panels

In the ***Map Overview*** tab of the right panel, users can inspect different metrics for the running simulation. These include vehicle count, average speed, and speed distribution.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/map_overview.png" alt="Image 2" style="height: 500px; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 6: Map Overview Menu
	</p>
</div>

In the ***Vehicles Data*** tab, users can inspect all metadata and metrics of all vehicle instances in the simulation in tabular form or pie chart form.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/vehicles_data_table.png" alt="Image 2" style="height: 500px; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 7: Vehicles Data Menu
	</p>
</div>

### Hotkeys

There are several keyboard inputs users can press to initiate certain actions quickly, without having to use their cursor or switch to a different UI tab. They can press:

- ***LEFT*** arrow key to cycle to the previous traffic light
- ***RIGHT*** arrow key to cycle to the next traffic light
- ***UP*** arrow key to immediately transition the current traffic light to the next phase
- ***DOWN*** arrow key to transition the current traffic light to the previous phase
- ***P*** to toggle Play/Pause for the simulation

### Exporting results

In the top right corner of the application window, there is an ***Export*** dropdown menu that allows users to export the current simulation state as either a portable document format (PDF) or comma separated values (CSV) file.

<div style="flex: 1 1 0; max-width: 100%;">
	<img src="https://github.com/hunggiadao/realtime-traffic-simulation-java-oop/blob/main/assets/export_menu.png" alt="Image 2" style="height: 120px; width: auto; max-width: 100%;">
	<p style="text-align: center; font-style: italic; font-size: 1em;">
		Figure 8: Export Dropdown Menu
	</p>
</div>

**NOTE**: the Export Menu is only available when the simulation is paused, since it relies on the immutability of states of values in the simulation. When the simulation is running, the menu is faded out.

## Notes

N/A

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
