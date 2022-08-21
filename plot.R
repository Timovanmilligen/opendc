require(tidyverse)
require(reshape2)
require(ggforce)
setwd("~/opendc2/Results")
#Load data
FF <- read.table("Baseline/FirstFit.txt",header = TRUE)
LCL <- read.table("Baseline/LowestCpuLoad.txt",header = TRUE)
LCD <- read.table("Baseline/LowestCpuDemand.txt",header = TRUE)
LML <- read.table("Baseline/LowestMemoryLoad.txt",header = TRUE)
MCL <- read.table("Baseline/MaximumConsolidationLoad.txt",header = TRUE)
VCPU <- read.table("Baseline/VCpuCapacity.txt",header = TRUE)
PS20 <- read.table("Baseline/Portfolio_Scheduler20m.txt",header = TRUE)
PS20History <- read.table("Baseline/Portfolio_Scheduler20m_history.txt")

PS40 <- read.table("Baseline/Portfolio_Scheduler40m.txt",header = TRUE)
PS40History <- read.table("Baseline/Portfolio_Scheduler40m_history.txt")

PS60 <- read.table("Baseline/Portfolio_Scheduler60m.txt",header = TRUE)
PS60History <- read.table("Baseline/Portfolio_Scheduler60m_history.txt")

PSExtended <- read.table("Extended/Portfolio_Scheduler20m.txt",header = TRUE)
PSExtendedHistory <- read.table("Extended/Portfolio_Scheduler20m_history.txt")

PSExtended2 <- read.table("Extended2/Portfolio_Scheduler20m.txt",header = TRUE)
PSExtended2History <- read.table("Extended2/Portfolio_Scheduler20m_history.txt")
#Add data ----------------------------------------
FF$cumulative_cpu_usage <- cumsum(FF$cpu_usage)
PS20$cumulative_cpu_usage <- cumsum(PS20$cpu_usage)
PS40$cumulative_cpu_usage <- cumsum(PS40$cpu_usage)
PS60$cumulative_cpu_usage <- cumsum(PS60$cpu_usage)
LCL$cumulative_cpu_usage <- cumsum(LCL$cpu_usage)
LCD$cumulative_cpu_usage <- cumsum(LCD$cpu_usage)
LML$cumulative_cpu_usage <- cumsum(LML$cpu_usage)
MCL$cumulative_cpu_usage <- cumsum(MCL$cpu_usage)
VCPU$cumulative_cpu_usage <- cumsum(VCPU$cpu_usage)
PSExtended$cumulative_cpu_usage <- cumsum(PSExtended$cpu_usage)
PSExtended2$cumulative_cpu_usage <- cumsum(PSExtended2$cpu_usage)

FF$Overprovisioned <- FF$cpu_demand/FF$cpu_usage
PS20$Overprovisioned <- PS20$cpu_demand/PS20$cpu_usage
PS40$Overprovisioned <- PS40$cpu_demand/PS40$cpu_usage
PS60$Overprovisioned <- PS60$cpu_demand/PS60$cpu_usage
LCL$Overprovisioned <- LCL$cpu_demand/LCL$cpu_usage
LCD$Overprovisioned <- LCD$cpu_demand/LCD$cpu_usage
LML$Overprovisioned <- LML$cpu_demand/LML$cpu_usage
MCL$Overprovisioned <- MCL$cpu_demand/MCL$cpu_usage
VCPU$Overprovisioned <- VCPU$cpu_demand/VCPU$cpu_usage
PSExtended$Overprovisioned <- PSExtended$cpu_demand/PSExtended$cpu_usage
PSExtended2$Overprovisioned <- PSExtended2$cpu_demand/PSExtended2$cpu_usage

#Combine data in data frame
combined_data <- FF %>%  mutate(Type = 'First Fit') %>%
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

combined_data$Type <- as.factor(combined_data$Type)
cdSubset <-  subset(x= combined_data, subset = Time_minutes%%10000 == 0)
portfolioSchedulers <-  PS20 %>%  mutate(Type = 'PS 20m') %>%
  bind_rows(PSExtended %>% 
              mutate(Type = "Extended PS"))

psDurations <-  PS20 %>%  mutate(Type = 'PS 20m') %>%
  bind_rows(PS40 %>% 
              mutate(Type = "PS 40m"))%>%
  bind_rows(PS60 %>% 
              mutate(Type = "PS 60m"))
#POWER PLOTS ---------------------------------------------------------------------------------------
#Power interval
ggplot(combined_data,aes(y = intermediate_powerdraw_kJ,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Powerdraw within time interval (5 minutes) since last timestamp")

#Total powerdraw
ggplot(combined_data,aes(y = total_powerdraw_kJ,x = Time_minutes,color = Type)) + 
  geom_line(aes(linetype = Type)) +
  scale_linetype_manual(values=c("dotdash", "longdash","dashed", "dashed","longdash", "twodash","dotted")) + 
  geom_point(data = cdSubset, aes(shape = Type)) +
  scale_shape_manual(values=1:nlevels(cdSubset$Type)) + xlab("Time (minutes)") + ylab("Total powerdraw (kJ)")
ggsave("Figures/PowerdrawBaseline.pdf")
#Overall host power efficiency
ggplot(combined_data,aes(y = overall_power_efficiency,x = Time_minutes,color = Type)) + 
  geom_line(aes(linetype = Type)) +
  scale_linetype_manual(values=c("dotdash", "longdash","dashed", "dashed","longdash", "twodash","dotted")) + 
  geom_point(data = cdSubset, aes(shape = Type)) +
  scale_shape_manual(values=1:nlevels(cdSubset$Type)) + 
  xlab("Time (minutes)") + 
  ylab("Overall power efficiency (Mhz/kJ)")

ggsave("Figures/PowerEfficiencyBaseline.pdf")

psSubset <-  subset(x= portfolioSchedulers, subset = Time_minutes%%10000 == 0)
ggplot(portfolioSchedulers,aes(y = overall_power_efficiency,x = Time_minutes,color = Type)) + 
  geom_line(aes(linetype = Type)) + 
  geom_point(data = psSubset, aes(shape = Type)) + xlab("Time (minutes)") + ylab("Overall power efficiency (Mhz/kJ)")

ggsave("Figures/PowerEfficiencyExtended.pdf")

psDurationSubset <-  subset(x= psDurations, subset = Time_minutes%%10000 == 0)
ggplot(psDurations,aes(y = overall_power_efficiency,x = Time_minutes,color = Type)) + 
  geom_line(aes(linetype = Type)) + 
  geom_point(data = psDurationSubset, aes(shape = Type)) + xlab("Time (minutes)") + ylab("Overall power efficiency (Mhz/kJ)")

ggsave("Figures/PowerEfficiencyDuration.pdf")

#------------------------------------------------------------------------------
ggplot(combined_data,aes(y = cpu_demand,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Cpu_demand")

#CPU demand
ggplot() + 
  geom_line(data=FF, aes(x=Time_minutes, y = cpu_usage),color = "green", alpha = 0.5)+
  geom_line(data=PS20, aes(x=Time_minutes, y = cpu_usage),color = "blue", alpha = 0.5)+
  ggtitle("Cpu demand MHz")  

#CPU Idle
combined_data$cpu_idle_time<- combined_data$cpu_idle_time/60
ggplot(combined_data,aes(y = cpu_idle_time,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Total cpu idle time (minutes)")

#CPU Usage
ggplot(combined_data,aes(y = cpu_usage,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Cpu usage (MHz)")

ggplot(combined_data,aes(y = cumulative_cpu_usage,x = Time_minutes,color = Type)) + 
  geom_line(aes(linetype = Type)) +
  scale_linetype_manual(values=c("dotdash", "longdash","dashed", "dashed","longdash", "twodash","dotted")) + 
  geom_point(data = cdSubset, aes(shape = Type)) +
  scale_shape_manual(values=1:nlevels(cdSubset$Type)) +xlab("Time (minutes)") + ylab("Cumulative CPU usage (Mhz)")

ggsave("Figures/CpuUsageBaseline.pdf")
#Demand/Usage ratio
ggplot(combined_data,aes(y = Overprovisioned,x = Time_minutes,color = Type)) + 
  geom_line() 

#Cpu demand and usage in one
df <- melt(PS20[c("Time_minutes","cpu_demand","cpu_usage")] ,  id.vars = 'Time_minutes', variable.name = 'Variable')
#create line plot for each column in data frame
ggplot(df, aes(Time_minutes, value)) +
  geom_line(aes(colour = Variable)) +
  ggtitle("Cpu demand and usage (MHz) Portfolio Scheduler")

#Change active scheduler names --------------------------
PS20History$V2 <- replace(PS20History$V2, PS20History$V2 == "Weighers:RamWeigher[multiplier=1.0],Filters:ComputeFilter-VCpuFilter[allocationRatio=16.0]-RamFilter[allocationRatio=1.0]subsetSize:1",
                          "LML")
PS20History$V2 <- replace(PS20History$V2, PS20History$V2 == "Weighers:MaximumConsolidationLoad[multiplier=1.0],Filters:ComputeFilter-VCpuFilter[allocationRatio=16.0]-RamFilter[allocationRatio=1.0]subsetSize:1",
                          "MCL")
PS20History$V2 <- replace(PS20History$V2, PS20History$V2 == "Weighers:FFWeigher[multiplier=1.0],Filters:ComputeFilter-VCpuFilter[allocationRatio=16.0]-RamFilter[allocationRatio=1.0]subsetSize:1",
                          "FF")
PS20History$V2 <- replace(PS20History$V2, PS20History$V2 == "Weighers:CpuLoadWeigher[multiplier=-1.0],Filters:ComputeFilter-VCpuFilter[allocationRatio=16.0]-RamFilter[allocationRatio=1.0]subsetSize:1",
                          "LCL")

PSExtendedHistory$V2 <- replace(PSExtendedHistory$V2, PSExtendedHistory$V2 == "Weighers:RamWeigher[multiplier=1.0],Filters:ComputeFilter-VCpuFilter[allocationRatio=16.0]-RamFilter[allocationRatio=1.0]subsetSize:1",
                          "LML")
PSExtendedHistory$V2 <- replace(PSExtendedHistory$V2, PSExtendedHistory$V2 == "Weighers:MaximumConsolidationLoad[multiplier=1.0],Filters:ComputeFilter-VCpuFilter[allocationRatio=16.0]-RamFilter[allocationRatio=1.0]subsetSize:1",
                          "MCL")
PSExtendedHistory$V2 <- replace(PSExtendedHistory$V2, PSExtendedHistory$V2 == "Weighers:FFWeigher[multiplier=1.0],Filters:ComputeFilter-VCpuFilter[allocationRatio=16.0]-RamFilter[allocationRatio=1.0]subsetSize:1",
                          "FF")
PSExtendedHistory$V2 <- replace(PSExtendedHistory$V2, PSExtendedHistory$V2 == "Weighers:CpuLoadWeigher[multiplier=-1.0],Filters:ComputeFilter-VCpuFilter[allocationRatio=16.0]-RamFilter[allocationRatio=1.0]subsetSize:1",
                          "LCL")
PSExtendedHistory$V2 <- replace(PSExtendedHistory$V2, PSExtendedHistory$V2 == "Weighers:InstanceCountWeigher[multiplier=0.9412430903700983],Filters:ComputeFilter-VCpuFilter[allocationRatio=24.0]-RamFilter[allocationRatio=1.0]subsetSize:9",
                                "Reflection Result")


#Active scheduler plots
ggplot(PS20History,aes(V1,V2,group = 1)) +
  geom_point() + geom_line() + xlab("Time (minutes)") + ylab("Active policy") 
ggsave("Figures/PolicyHistory.pdf")
ggplot(PSExtendedHistory,aes(V1,V2,group = 1)) +
  geom_point() + geom_line()

ggplot(PSExtended2History,aes(V1,V2,group = 1)) +
  geom_point() + geom_line()
#Service metrics
ggplot(combined_data,aes(y = servers_active,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("active servers")

#Policy distribution
ggplot(PS20History, aes(x = V2)) +  
  geom_bar(aes(y = (..count..)/sum(..count..))) +ylab("Frequency") + xlab("Policy")
ggsave("Figures/BaselinePolicyFrequency.pdf")

ggplot(PSExtendedHistory, aes(x = V2)) +  
  geom_bar(aes(y = (..count..)/sum(..count..))) +ylab("Frequency") + xlab("Policy")
ggsave("Figures/ExtendedPolicyFrequency.pdf")
#Clear memory and call garbage collector
rm(list=ls()) 
gc()