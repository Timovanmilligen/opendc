require(tidyverse)
setwd("~/opendc2/opendc-experiments/opendc-experiments-timo/src/main/resources/output")
main_trace <- read.table("main_trace.txt",header = TRUE)

ggplot(data = main_trace, mapping = aes(x = Timestamp, y = Power_draw_since_previous))

clearMemory()
#rm(list=ls()) 
gc()