# MagPDR_Server
A real-time positioning server system (not supports multiple users yet). It combines my previous projects and rebuild them in Java:
## Core Threads
+ Thread 1. Receiving  sensors data from my App by sockte/TCP
+ Thread 2. Using the sensors data to run the PDR
+ Thread 3. Using the sensors and PDR xy data to run the MagPDR
## Non-core Threads
+ Thread 4: Painting the MagPDR positioning results
+ Thread 5: Saving the results to database (MySql)
