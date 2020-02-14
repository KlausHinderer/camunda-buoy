# camunda-buoy
Add idempotence to your camunda processes.

# About
Not all resources of your ProcessDelegates participate in the same transaction (provided non-XA transactions are used). If a technical error occurs, you have a problem. The process cannot be restarted because some parts have already been written to the resources. The solution is to use idempotent ProcessDelegates.

You can implement all ProcessDelegates with idempotence in your mind. Or you can use this project.

# How it works
The restarted process instance has to take the same path through the ProcessDefinition. Exclusive Gateways should decide based on the process' variables. This project provides special behavior for the idempotent ProcessDelegates:
 1. It can detect whether the invocation is a retry or the first attempt
 2. If it is the first attempt, your code is called and after that, all process variables are written to some non-transactional storage
 3. If it is a retry, the variables are read from the storage and set on the DelegateExecution. The logic of the delegate is not called

The implementation of the ProcessDelegate can be oblivious to retries and idempotence, making the development of the logic much easier.

# Persistent storage
The persistent storage of the variables has to be non-transactional (as the tx can be rolled back later, whereas the data in the storage must remain). Currently the following technologies are planned:
 1. Files in the file system. This is a node-local storage, forcing you to restart a failed process instance on the same node
 2. Redis. Cluster-wide visibility of the data in the storage
 3. Custom implementation. This is up to you

# Performance
The performance depends on the amount and size of your process variables. Performance can be tweaked a bit by the bufferSize. I estimate the minimum overhead (only a handful of small variables in the process instance) to be about 60 us (microseconds) and typical overhead of 100-200 us. This overhead occurs on each ProcessDelegate that uses idempotence. 

The typical path is that the process variables are written to the storage, since a read only occurs on retries. The read may not be as optimized as the write. 
 
# Interaction with the camunda-epsilon-serializer
For performance reasons this project (buoy) depends on camunda serializing all the process variables. Thus this project cannot be used in combination with the epsilon-serializer. 

# Project status
In development, not mature for production.