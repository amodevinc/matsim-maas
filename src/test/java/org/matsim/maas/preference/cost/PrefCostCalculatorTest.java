package org.matsim.maas.preference.cost;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.optimizer.insertion.InsertionGenerator;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.maas.preference.data.UserPreferenceStore;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PrefCostCalculatorTest {

    private PrefCostCalculator calculator;
    private UserPreferenceStore mockStore;
    private DrtRequest mockRequest;
    private InsertionGenerator.Insertion mockInsertion;

    @BeforeEach
    void setUp() {
        mockStore = mock(UserPreferenceStore.class);
        calculator = new PrefCostCalculator(mockStore, false);
        mockRequest = mock(DrtRequest.class);
        mockInsertion = mock(InsertionGenerator.Insertion.class);

        // Setup common mocks
        Id<Person> personId = Id.createPersonId("testPerson");
        when(mockRequest.getPassengerIds()).thenReturn(java.util.Arrays.asList(personId));
    }

    @Test
    void testCalculateWithoutPreferences() {
        // When preferences are disabled, should use simple base cost
        double cost = calculator.calculate(mockRequest, mockInsertion, null);
        assertEquals(60.0, cost, 0.1); // Base cost only
    }

    @Test
    void testCalculateWithPreferencesButNoData() {
        // No preference data, should use defaults
        when(mockStore.getUserPreference(any())).thenReturn(null);

        calculator = new PrefCostCalculator(mockStore, true);
        double cost = calculator.calculate(mockRequest, mockInsertion, null);
        
        // Expected: base cost (60) + default cost calculation
        assertTrue(cost > 60.0, "Cost should be greater than base cost when preferences enabled");
    }

    @Test
    void testCalculateWithPreferences() {
        // Setup preference data
        Id<Person> personId = Id.createPersonId("testPerson");
        UserPreferenceData mockPrefData = mock(UserPreferenceData.class);
        when(mockStore.getUserPreference(personId)).thenReturn(mockPrefData);
        when(mockPrefData.calculateUtility(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(-500.0);

        calculator = new PrefCostCalculator(mockStore, true); // Enable preferences
        double cost = calculator.calculate(mockRequest, mockInsertion, null);
        
        // Expected: base + abs(prefCost) = 60 + 500 = 560
        assertEquals(560.0, cost, 0.1);
    }

    @Test
    void testIsUsingPreferenceWeights() {
        assertFalse(calculator.isUsingPreferenceWeights());
        
        PrefCostCalculator prefCalculator = new PrefCostCalculator(mockStore, true);
        assertTrue(prefCalculator.isUsingPreferenceWeights());
    }

    @Test
    void testGetStats() {
        PrefCostCalculator.PrefCostStats stats = calculator.getStats();
        assertNotNull(stats);
        assertFalse(stats.isUsingPreferenceWeights());
    }
} 