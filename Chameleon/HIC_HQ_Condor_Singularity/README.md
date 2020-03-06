# Chameleon template for HIC_HQ Workflow

This folder includs the Heat template to initiate a HTCondor cluster on Chameleon. Users may use this cluster to run the HIC_HQ workflow with the support of Pegasus WMS.

## Initate cluster on Chameleon

* Go to chameleoncloud.org. Choose either UC or TACC site.
* Choose Orchestration -> Stacks.
* Select "Launch Stack".
* Browse and locate `Condor_singularity_template.txt`
* Enter or select desired parameters.
* Launch stack. The Condor cluster should be ready when the stack initiation is complete.


## Bring up a Condor cluster with both Chameleon and ExoGENI nodes

The `Exogeni_Condor_singularity_template` allows setting up a Condor pool that is across Chameleon and ExoGENI testbeds. Use Exogeni_Condor_singularity_template.txt to initiate the Condor cluster. Choose `exogeni` as Network Provider.

Once the Chameleon cluster is up, configure an ExoGENI slice of workers, install condor, configure its keys and /etc/hosts, so the new node will join the Condor pool. Detailed instructions will be added.



## Run the Pegasus HIC_HQ workflow

* Log into the master node. And check status. The following output indicates the condor pool is successfully initated and two workers have joined:

	```
	cc@master:~$ condor_status
   	Name                     OpSys      Arch   State     Activity LoadAv Mem     ActvtyTime

	slot1@worker-0.novalocal LINUX      X86_64 Unclaimed Idle      0.000 128705  1+03:04:50
	slot1@worker-1.novalocal LINUX      X86_64 Unclaimed Idle      0.000 128705  1+03:04:47

                     Total Owner Claimed Unclaimed Matched Preempting Backfill  Drain

        X86_64/LINUX     2     0       0         2       0          0        0      0

               Total     2     0       0         2       0          0        0      0

* Run HIC_HQ workflow:
	
	```
	$ cd HIC-HQ-Workflow/workflow/
	$ chmod 777 run.sh
	$ ./run.sh
	```
