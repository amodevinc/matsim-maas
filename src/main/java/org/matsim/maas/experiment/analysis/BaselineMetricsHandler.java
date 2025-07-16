package org.matsim.maas.experiment.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEvent;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEventHandler;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEventHandler;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEventHandler;
// BasicEventHandler not needed
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;

// javax.inject.Inject not needed
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive metrics collection for baseline DRT performance analysis.
 * Tracks key performance indicators including service rates, wait times,
 * and overall system performance for comparison with preference-aware algorithms.
 */
public class BaselineMetricsHandler implements 
    DrtRequestSubmittedEventHandler,
    PassengerRequestScheduledEventHandler,
    PassengerRequestRejectedEventHandler,
    PersonDepartureEventHandler,
    PersonArrivalEventHandler,
    IterationEndsListener {

    private final String outputDirectory;
    
    // Request tracking
    private final Map<Id<org.matsim.contrib.dvrp.optimizer.Request>, Double> requestSubmissionTimes = new HashMap<>();
    private final Map<Id<org.matsim.contrib.dvrp.optimizer.Request>, Double> requestScheduledTimes = new HashMap<>();
    private final Map<Id<Person>, Double> drtDepartureTimes = new HashMap<>();
    
    // Metrics counters
    private int requestsSubmitted = 0;
    private int requestsScheduled = 0;
    private int requestsRejected = 0;
    private double totalWaitTime = 0.0;
    private double totalTravelTime = 0.0;
    private int completedTrips = 0;
    
    public BaselineMetricsHandler(String outputDirectory) {
        this.outputDirectory = outputDirectory;
        System.out.println("BaselineMetricsHandler: Initialized for experiment tracking");
    }

    @Override
    public void handleEvent(DrtRequestSubmittedEvent event) {
        requestSubmissionTimes.put(event.getRequestId(), event.getTime());
        requestsSubmitted++;
    }

    @Override
    public void handleEvent(PassengerRequestScheduledEvent event) {
        Double submissionTime = requestSubmissionTimes.get(event.getRequestId());
        if (submissionTime != null) {
            requestScheduledTimes.put(event.getRequestId(), event.getTime());
            double waitTime = event.getTime() - submissionTime;
            totalWaitTime += waitTime;
            requestsScheduled++;
        }
    }

    @Override
    public void handleEvent(PassengerRequestRejectedEvent event) {
        requestsRejected++;
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        if ("drt".equals(event.getLegMode())) {
            drtDepartureTimes.put(event.getPersonId(), event.getTime());
        }
    }

    @Override
    public void handleEvent(PersonArrivalEvent event) {
        if ("drt".equals(event.getLegMode())) {
            Double departureTime = drtDepartureTimes.get(event.getPersonId());
            if (departureTime != null) {
                double travelTime = event.getTime() - departureTime;
                totalTravelTime += travelTime;
                completedTrips++;
                drtDepartureTimes.remove(event.getPersonId());
            }
        }
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        int iteration = event.getIteration();
        
        // Calculate metrics
        double serviceRate = requestsSubmitted > 0 ? (double) requestsScheduled / requestsSubmitted : 0.0;
        double rejectionRate = requestsSubmitted > 0 ? (double) requestsRejected / requestsSubmitted : 0.0;
        double avgWaitTime = requestsScheduled > 0 ? totalWaitTime / requestsScheduled : 0.0;
        double avgTravelTime = completedTrips > 0 ? totalTravelTime / completedTrips : 0.0;
        
        // Log to console
        System.out.println("\n=== BASELINE METRICS - Iteration " + iteration + " ===");
        System.out.printf("Requests submitted: %d%n", requestsSubmitted);
        System.out.printf("Requests scheduled: %d%n", requestsScheduled);
        System.out.printf("Requests rejected: %d%n", requestsRejected);
        System.out.printf("Service rate: %.3f%n", serviceRate);
        System.out.printf("Rejection rate: %.3f%n", rejectionRate);
        System.out.printf("Average wait time: %.1f seconds%n", avgWaitTime);
        System.out.printf("Average travel time: %.1f seconds%n", avgTravelTime);
        System.out.printf("Completed trips: %d%n", completedTrips);
        System.out.println("================================================\n");
        
        // Write to CSV file
        writeMetricsToFile(iteration, serviceRate, rejectionRate, avgWaitTime, avgTravelTime);
        
        // Reset counters for next iteration
        resetCounters();
    }
    
    private void writeMetricsToFile(int iteration, double serviceRate, double rejectionRate, 
                                   double avgWaitTime, double avgTravelTime) {
        String filename = outputDirectory + "/baseline_metrics.csv";
        boolean fileExists = Files.exists(Paths.get(filename));
        
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename), 
                                                              StandardOpenOption.CREATE, 
                                                              StandardOpenOption.APPEND)) {
            
            // Write header if file is new
            if (!fileExists) {
                writer.write("iteration,requests_submitted,requests_scheduled,requests_rejected," +
                           "service_rate,rejection_rate,avg_wait_time_sec,avg_travel_time_sec," +
                           "completed_trips\n");
            }
            
            // Write metrics data
            writer.write(String.format("%d,%d,%d,%d,%.4f,%.4f,%.2f,%.2f,%d%n",
                                     iteration, requestsSubmitted, requestsScheduled, requestsRejected,
                                     serviceRate, rejectionRate, avgWaitTime, avgTravelTime, completedTrips));
            
        } catch (IOException e) {
            System.err.println("Error writing baseline metrics: " + e.getMessage());
        }
    }
    
    private void resetCounters() {
        requestSubmissionTimes.clear();
        requestScheduledTimes.clear();
        drtDepartureTimes.clear();
        
        requestsSubmitted = 0;
        requestsScheduled = 0;
        requestsRejected = 0;
        totalWaitTime = 0.0;
        totalTravelTime = 0.0;
        completedTrips = 0;
    }

    @Override
    public void reset(int iteration) {
        // Reset is handled in notifyIterationEnds to ensure metrics are calculated first
    }
}