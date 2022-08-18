require(tidyverse)
require(reshape2)
require(ggforce)
setwd("~/opendc2/Results")
#Load data
baseline <- head(read.table("New results/Baseline/genetic_search.txt",header = TRUE),-1)
baseline$Best_fitness<- baseline$Best_fitness/100000
ggplot(baseline,aes(y = Best_fitness,x = Generation)) + 
  geom_line(color = "blue") +ylab("Improved Energy Efficiency")


extended <- head(read.table("New results/Extended/genetic_search.txt",header = TRUE),-1)
extended$Best_fitness<- extended$Best_fitness/100000

ggplot(extended,aes(y = Best_fitness,x = Generation)) + 
  geom_line(color = "blue") +ylab("Improved Energy Efficiency")



#BITBRAINS -----------------------------------------------------------------
bb_baseline <- head(read.table("bitbrains/genetic_search.txt",header = TRUE),-1)
bb_baseline$Best_fitness<- bb_baseline$Best_fitness/100000

bb_extended <- head(read.table("bitbrains/extended/genetic_search.txt",header = TRUE),-1)
bb_extended$Best_fitness<- bb_extended$Best_fitness/100000

ggplot(bb_baseline,aes(y = Best_fitness,x = Generation)) + 
  geom_line(color = "blue") +ylab("Improved Energy Efficiency")

ggplot(bb_extended,aes(y = Best_fitness,x = Generation)) + 
  geom_line(color = "blue") +ylab("Improved Energy Efficiency")
