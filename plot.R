require(tidyverse)
require(reshape2)
setwd("~/GitHub/opendc/opendc-experiments/opendc-experiments-timo/src/main/resources/output")
FF <- read.table("First_Fit.txt",header = TRUE)
PS <- read.table("Portfolio_Scheduler10m.txt",header = TRUE)
PSHistory <- read.table("Portfolio_Scheduler10m_history.txt",header = TRUE)
PSHistory$Active_scheduler<- as.factor(PSHistory$Active_scheduler)
PS$Overprovisioned <- PS$cpu_demand/PS$cpu_usage

combined_data <- FF %>%  mutate(Type = 'First Fit') %>%
  bind_rows(PS %>%
              mutate(Type = 'Portfolio Scheduler'))

#Power
ggplot(combined_data,aes(y = intermediate_powerdraw_kJ,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Powerdraw within time interval (5 minutes) since last timestamp")
ggplot(combined_data,aes(y = total_powerdraw_kJ,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Total powerdraw per scheduler over time")

ggplot(combined_data,aes(y = cpu_demand,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Cpu_demand")

#CPU demand
ggplot() + 
  geom_line(data=FF, aes(x=Time_minutes, y = cpu_usage),color = "green", alpha = 0.5)+
  geom_line(data=PS, aes(x=Time_minutes, y = cpu_usage),color = "blue", alpha = 0.5)+
  ggtitle("Cpu demand MHz")  

#CPU Idle
ggplot(combined_data,aes(y = cpu_idle_time,x = Time_minutes,color = Type)) + 
  geom_line() +
  ggtitle("Cpu idle time (seconds)")

#CPU Idle
ggplot(combined_data,aes(y = cpu_usage,x = Time_minutes,color = Type)) + 
  geom_line(linetype = "dashed") +
  ggtitle("Cpu usage (MHz)")

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

PSHistory$Time_minutes <- PSHistory$Time_minutes/60000

ggplot(PSHistory,aes(Time_minutes,Active_scheduler,group = 1)) +
  geom_point() + geom_line()
#Active scheduler plot
plot(PSHistory)


#Clear memory and call garbage collector
rm(list=ls()) 
gc()

