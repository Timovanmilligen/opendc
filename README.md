
# Reproducing the experiments
The final thesis is included in this repository under 'Final Thesis.pdf'.
To reproduce the experiments run to produce the results in the experiments section of the thesis follow the steps below.

## Setup
1. Clone this repository
2. Download the data needed to run the experiments from https://zenodo.org/record/7115464. The results are also included in this file.
3. Unzip the downloaded folder
4. Copy the 'Snapshots' folder from the unzipped files into the cloned repository into the resources folder at the file path: 'opendc-experiments\opendc-experiments-timo\src\main\resources\'
5. Install Java SDK 17 on your machine from https://www.oracle.com/java/technologies/downloads/#java17
6. Install gradle on your machine https://gradle.org/install/ if you want to run all experiments after each other or IntelliJ idea https://www.jetbrains.com/idea/download/#section=windows if you wish to run them seperately.

## Running the experiments
There are a few options for running the experiments, you can run them all after each other using gradle, or run them seperately in IntelliJ IDEA.
**Run all experiments using gradle**
1.  Open a command prompt from the root folder of the cloned repository.
2.  Run `gradle opendc-experiments:opendc-experiments-timo:experiment` from the root folder of the cloned repository.

**Run single experiments**
To run a specific experiment
1. Open the root folder of the cloned repository as an IntelliJ IDEA project.
2. Navigate to `opendc-experiments\opendc-experiments-timo\src\main\kotlin\org\opendc\experiments\timo` in IntelliJ IDEA to see all experiments
3. Open an experiment class, for example `SolvinityBaselineExperiment.kt`, and run it in IntelliJ IDEA by clicking the arrows next to the class definition and clicking 'Run SolvinityBaselineExperiment'
