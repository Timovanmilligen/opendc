require(tidyverse)
require(reshape2)
require(ggforce)
setwd("~/opendc2/Results")

workload <- "Solvinity/Baseline/"
#Load data
FF <- read.table(paste(workload, "FirstFit_serverData.txt",sep = ""),header = TRUE)
LCL <- read.table(paste(workload,"LowestCpuLoad_serverData.txt",sep = ""),header = TRUE)
LCD <- read.table(paste(workload,"LowestCpuDemand_serverData.txt",sep = ""),header = TRUE)
LML <- read.table(paste(workload,"LowestMemoryLoad_serverData.txt",sep = ""),header = TRUE)
MCL <- read.table(paste(workload,"MaximumConsolidationLoad_serverData.txt",sep = ""),header = TRUE)
VCPU <- read.table(paste(workload,"VCpuCapacity_serverData.txt",sep = ""),header = TRUE)
PS20 <- read.table(paste(workload,"Portfolio_Scheduler20m_serverData.txt",sep = ""),header = TRUE)


combined_data <- FF %>%  mutate(Type = 'FF') %>%
  bind_rows(PS20 %>%
              mutate(Type = 'PS 20m')) %>% 
  bind_rows(LCL %>% 
              mutate(Type = "LCL")) %>% 
  bind_rows(LCD %>% 
              mutate(Type = "LCD")) %>% 
  bind_rows(MCL %>% 
              mutate(Type = "MCL")) %>% 
  bind_rows(VCPU %>% 
              mutate(Type = "VCPU")) %>% 
  bind_rows(LML %>% 
              mutate(Type = "LML"))
combined_data$cpu_steal = combined_data$cpu_steal/60  
combined_data$cpu_lost = combined_data$cpu_lost/60  
combined_data$Type <- as.factor(combined_data$Type)
combined_data$waitTime <- (combined_data$boot_time- combined_data$provision_time)/60000
data_summary <- function(x) {
  m <- mean(x)
  ymax <- m+sd(x)
  return(c(y=m,ymin=0,ymax=ymax))
}
mean(FF$cpu_steal/60)
mean(LML$cpu_steal/60)
mean(LCL$cpu_steal/60)
mean(LCD$cpu_steal/60)
mean(MCL$cpu_steal/60)
mean(VCPU$cpu_steal/60)
mean(PS20$cpu_steal/60)
sd(FF$cpu_steal/60)
sd(LML$cpu_steal/60)
sd(LCL$cpu_steal/60)
sd(LCD$cpu_steal/60)
sd(MCL$cpu_steal/60)
sd(VCPU$cpu_steal/60)
sd(PS20$cpu_steal/60)

mean(FF$cpu_lost/60)
mean(LML$cpu_lost/60)
mean(LCL$cpu_lost/60)
mean(LCD$cpu_lost/60)
mean(MCL$cpu_lost/60)
mean(VCPU$cpu_lost/60)
mean(PS20$cpu_lost/60)
sd(FF$cpu_lost/60)
sd(LML$cpu_lost/60)
sd(LCL$cpu_lost/60)
sd(LCD$cpu_lost/60)
sd(MCL$cpu_lost/60)
sd(VCPU$cpu_lost/60)
sd(PS20$cpu_lost/60)

ggplot(combined_data, aes(x=Type, y = cpu_steal, fill = Type)) + 
  geom_violin(trim=TRUE,scale = "width")+ 
  stat_summary(fun.data=data_summary, geom="pointrange", color = "red")+
  scale_fill_brewer(palette="Blues") + 
  theme_classic() +ylab("Cpu Steal Time (Minutes)") + xlab("Policy")


ggplot(combined_data[(combined_data$cpu_lost<1000),], aes(x=Type, y = cpu_lost, fill = Type)) + 
  geom_violin(trim=TRUE,scale = "width")+ 
  stat_summary(fun.data=data_summary, geom="pointrange", color = "red")+
  scale_fill_brewer(palette="Blues") + 
  theme_classic() +ylab("Cpu Lost Time (Minutes)") + xlab("Policy")


count(combined_data[(combined_data$cpu_steal<10000),])
combined_data[(combined_data$cpu_steal>100),]$cpu_steal
count(LML[(LML$cpu_steal>=5000),])
count(combined_data[(combined_data$cpu_lost<4000),])
combined_data[(combined_data$cpu_lost>4000),]$cpu_lost