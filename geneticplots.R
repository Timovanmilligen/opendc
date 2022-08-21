require(tidyverse)
require(reshape2)
require(ggforce)
require(gridExtra)
require(cowplot)
setwd("~/opendc2/Results")
#Load data
#BASELINE -----------------------------------------------------------------------------
baseline <- head(read.table("New results/Baseline/genetic_search.txt",header = TRUE),-1)
baseline$Best_fitness<- baseline$Best_fitness/100000
baseline$active_weigher <- "InstanceCountWeigher"
baseline$active_weigher <- as.factor(baseline$active_weigher)
baseline$subset <- 29
baseline$subset[20:48] <- 24
p1<-ggplot(baseline,aes(y = Best_fitness,x = Generation)) +
  geom_vline(xintercept = 10, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
             color = "red", size=0.5)+
  geom_line(color = "blue") +ylab("Improved Energy Efficiency")
p2<-ggplot(baseline,aes(y = vCpuOvercommit,x = Generation)) + 
  geom_vline(xintercept = 10, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
             color = "red", size=0.5)+
  geom_line() + theme(axis.title.x=element_blank(),
                      axis.text.x=element_blank(),
                      axis.ticks.x=element_blank()) 
#Active weigher plots
p3<-ggplot(baseline,aes(Generation,active_weigher,group = 1)) +
  geom_point() + geom_line() +
  geom_vline(xintercept = 10, linetype="dashed", 
                                         color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
              color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
              color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
              color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
              color = "red", size=0.5)+ 
  xlab("Time (minutes)") + ylab("Active Weighers") +
  theme(axis.title.x=element_blank(),
        axis.text.x=element_blank(),
        axis.ticks.x=element_blank() 
  )

#subset plot
p4 <-ggplot(baseline,aes(y = subset,x = Generation)) + 
  geom_vline(xintercept = 10, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
             color = "red", size=0.5)+
  geom_line() + theme(axis.title.x=element_blank(),
                      axis.text.x=element_blank(),
                      axis.ticks.x=element_blank()) 
aligned <- align_plots(p1, p2,p3,p4, align = "v")
grid.arrange(aligned[[4]],aligned[[3]],aligned[[2]],aligned[[1]],nrow = 4)
ggsave("Figures/BaselineGeneticProgress.pdf")

#ggplot(baseline, aes(x = 1, y = factor(active_weigher))) + 
 # geom_bar(stat = "identity")
extended <- head(read.table("New results/Extended/genetic_search.txt",header = TRUE),-1)
extended$Best_fitness<- extended$Best_fitness/100000
ggplot(extended,aes(y = Best_fitness,x = Generation)) + 
  geom_line(color = "blue") +ylab("Improved Energy Efficiency")
ggsave("Figures/ExtendedGeneticProgress.pdf")


#BITBRAINS -----------------------------------------------------------------
baseline <- head(read.table("bitbrains/genetic_search.txt",header = TRUE),-1)
baseline$Best_fitness<- baseline$Best_fitness/100000
baseline$active_weigher <- "CoreRamWeigher"
baseline$active_weigher <- as.factor(baseline$active_weigher)
baseline$subset <- 4
baseline$subset[22:50] <- 13
p1<-ggplot(baseline,aes(y = Best_fitness,x = Generation)) +
  geom_vline(xintercept = 10, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
             color = "red", size=0.5)+
  geom_line(color = "blue") +ylab("Improved Energy Efficiency")
p2<-ggplot(baseline,aes(y = vCpuOvercommit,x = Generation)) + 
  geom_vline(xintercept = 10, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
             color = "red", size=0.5)+
  geom_line() + theme(axis.title.x=element_blank(),
                      axis.text.x=element_blank(),
                      axis.ticks.x=element_blank()) 
#Active weigher plots
p3<-ggplot(baseline,aes(Generation,active_weigher,group = 1)) +
  geom_point() + geom_line() +
  geom_vline(xintercept = 10, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
             color = "red", size=0.5)+ 
  xlab("Time (minutes)") + ylab("Active Weighers") +
  theme(axis.title.x=element_blank(),
        axis.text.x=element_blank(),
        axis.ticks.x=element_blank() 
  )

#subset plot
p4 <-ggplot(baseline,aes(y = subset,x = Generation)) + 
  geom_vline(xintercept = 10, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
             color = "red", size=0.5)+
  geom_line() + theme(axis.title.x=element_blank(),
                      axis.text.x=element_blank(),
                      axis.ticks.x=element_blank()) 
aligned <- align_plots(p1, p2,p3,p4, align = "v")
grid.arrange(aligned[[4]],aligned[[3]],aligned[[2]],aligned[[1]],nrow = 4)
ggsave("Figures/BitBrainsBaselineGeneticProgress.pdf")

#BITBRAINS EXTENDED--------------------------------------------------

bb_extended <- head(read.table("bitbrains/extended/genetic_search.txt",header = TRUE),-1)
bb_extended$Best_fitness<- bb_extended$Best_fitness/100000
bb_extended$active_weigher <- "CoreRamWeigher"
bb_extended$active_weigher[23:50] <- "CoreRamWeigher[-0.91]+VCPUWeigher[0.52]"
bb_extended$active_weigher <- as.factor(bb_extended$active_weigher)
bb_extended$subset <- 4
bb_extended$subset[9] <- 2
bb_extended$subset[10] <- 2
bb_extended$subset[16] <- 2
p1<-ggplot(bb_extended,aes(y = Best_fitness,x = Generation)) +
  geom_vline(xintercept = 10, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
             color = "red", size=0.5)+
  geom_line(color = "blue") +ylab("Improved Energy Efficiency")
p2<-ggplot(bb_extended,aes(y = vCpuOvercommit,x = Generation)) + 
  geom_vline(xintercept = 10, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
             color = "red", size=0.5)+
  geom_line() + theme(axis.title.x=element_blank(),
                      axis.text.x=element_blank(),
                      axis.ticks.x=element_blank()) 
#Active weigher plots
p3<-ggplot(bb_extended,aes(Generation,active_weigher,group = 1)) +
  geom_point() + geom_line() +
  geom_vline(xintercept = 10, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
             color = "red", size=0.5)+ 
  xlab("Time (minutes)") + ylab("Active Weighers") +
  theme(axis.title.x=element_blank(),
        axis.text.x=element_blank(),
        axis.ticks.x=element_blank() 
  )

#subset plot
p4 <-ggplot(bb_extended,aes(y = subset,x = Generation)) + 
  geom_vline(xintercept = 10, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 20, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 30, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 40, linetype="dashed", 
             color = "red", size=0.5)+
  geom_vline(xintercept = 50, linetype="dashed", 
             color = "red", size=0.5)+
  geom_line() + theme(axis.title.x=element_blank(),
                      axis.text.x=element_blank(),
                      axis.ticks.x=element_blank()) 
aligned <- align_plots(p1, p2,p3,p4, align = "v")
grid.arrange(aligned[[4]],aligned[[3]],aligned[[2]],aligned[[1]],nrow = 4)
ggsave("Figures/BitBrainsBaselineGeneticProgress.pdf")
