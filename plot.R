require(tidyverse)
require(reshape2)
setwd("~/opendc2/opendc-experiments/opendc-experiments-timo/src/main/resources/output")
FF <- read.table("First_Fit.txt",header = TRUE)
PS <- read.table("Portfolio_Scheduler20m.txt",header = TRUE)
LCL <- read.table("LowestCpuLoad.txt",header = TRUE)
LML <- read.table("LowestMemoryLoad.txt",header = TRUE)
PSHistory <- read.table("Portfolio_Scheduler20m_history.txt")
PSHistory$Active_scheduler<- as.factor(PSHistory$Active_scheduler)
PS$Overprovisioned <- PS$cpu_demand/PS$cpu_usage

#Add data ----------------------------------------
FF$cumulative_cpu_usage <- cumsum(FF$cpu_usage)
PS$cumulative_cpu_usage <- cumsum(PS$cpu_usage)
LCL$cumulative_cpu_usage <- cumsum(LCL$cpu_usage)
LML$cumulative_cpu_usage <- cumsum(LML$cpu_usage)
combined_data <- FF %>%  mutate(Type = 'First Fit') %>%
  bind_rows(PS %>%
              mutate(Type = 'Portfolio Scheduler')) %>% 
  bind_rows(LCL %>% 
              mutate(Type = "LCL")) #%>% 
  #bind_rows(LML %>% 
  #            mutate(Type = "LML"))
                              

#POWER PLOTS ---------------------------------------------------------------------------------------
#Power interval
ggplot(combined_data,aes(y = intermediate_powerdraw_kJ,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Powerdraw within time interval (5 minutes) since last timestamp")

#Total powerdraw
ggplot(combined_data,aes(y = total_powerdraw_kJ,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Total powerdraw per scheduler over time")

#Overall host power efficiency
ggplot(combined_data,aes(y = overall_power_efficiency,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Overall power efficiency")

#Intermediate host power efficiency
ggplot(combined_data,aes(y = intermediate_power_efficiency,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Intermediate host power efficiency")


ggplot(combined_data,aes(y = cpu_demand,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Cpu_demand")

#CPU demand
ggplot() + 
  geom_line(data=FF, aes(x=Time_minutes, y = cpu_usage),color = "green", alpha = 0.5)+
  geom_line(data=PS, aes(x=Time_minutes, y = cpu_usage),color = "blue", alpha = 0.5)+
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
  geom_line() +
  ggtitle("Cumulative cpu usage (MHz)")

#Demand/Usage ratio
combined_data$Overprovisioned <- combined_data$cpu_demand/combined_data$cpu_usage
ggplot(combined_data,aes(y = Overprovisioned,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Overprovisioned ratio (cpu demand / cpu usage")

#Cpu demand and usage in one
df <- melt(PS[c("Time_minutes","cpu_demand","cpu_usage")] ,  id.vars = 'Time_minutes', variable.name = 'Variable')
#create line plot for each column in data frame
ggplot(df, aes(Time_minutes, value)) +
  geom_line(aes(colour = Variable)) +
  ggtitle("Cpu demand and usage (MHz) Portfolio Scheduler")


#Active scheduler plot
ggplot(PSHistory,aes(V1,V2,group = 1)) +
  geom_point() + geom_line()

#Service metrics
ggplot(combined_data,aes(y = servers_active,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("active servers")

tail(PS$total_powerdraw_kJ)
tail(FF$total_powerdraw_kJ)
tail(LCL$total_powerdraw_kJ)
sum(PS$cpu_usage)
sum(FF$cpu_usage)
sum(LCL$cpu_usage)
#Clear memory and call garbage collector
rm(list=ls()) 
gc()

