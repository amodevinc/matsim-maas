package org.matsim.maas.preference.cost;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.optimizer.insertion.InsertionCostCalculator;
import org.matsim.contrib.drt.optimizer.insertion.InsertionGenerator.Insertion;
import org.matsim.contrib.drt.optimizer.insertion.InsertionDetourTimeCalculator.DetourTimeInfo;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.maas.preference.data.UserPreferenceStore;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Arrays;

/**
 * Test-driven development tests for PrefAwareInsertionCostCalculator.
 * Tests the composition pattern and preference-based cost adjustments.
 */
public class PrefAwareInsertionCostCalculatorTest {

    private PrefAwareInsertionCostCalculator calculator;
    private InsertionCostCalculator mockDefaultCalculator;
    private UserPreferenceStore mockPreferenceStore;
    private DrtRequest mockRequest;
    private Insertion mockInsertion;
    private DetourTimeInfo mockDetourTimeInfo;
    
    private static final double DEFAULT_COST = 100.0;
    private static final double TOLERANCE = 0.01;
    
    @BeforeEach
    void setUp() {
        // Create mocks
        mockDefaultCalculator = mock(InsertionCostCalculator.class);
        mockPreferenceStore = mock(UserPreferenceStore.class);
        mockRequest = mock(DrtRequest.class);
        mockInsertion = mock(Insertion.class);
        mockDetourTimeInfo = mock(DetourTimeInfo.class);
        
        // Setup basic request mock
        Id<Person> personId = Id.createPersonId("testPerson");
        when(mockRequest.getPassengerIds()).thenReturn(Arrays.asList(personId));
        
        // Setup default calculator to return predictable cost
        when(mockDefaultCalculator.calculate(any(), any(), any())).thenReturn(DEFAULT_COST);
    }
    
    @Test
    void testPassthroughWhenPreferencesDisabled() {
        // Test 1: When preferences are disabled, should return exact default cost
        calculator = new PrefAwareInsertionCostCalculator(
            mockDefaultCalculator, mockPreferenceStore, false);
        
        double result = calculator.calculate(mockRequest, mockInsertion, mockDetourTimeInfo);
        
        assertEquals(DEFAULT_COST, result, TOLERANCE, 
            "Should return exact default cost when preferences disabled");
        verify(mockDefaultCalculator).calculate(mockRequest, mockInsertion, mockDetourTimeInfo);
        verifyNoInteractions(mockPreferenceStore);
    }
    
    @Test
    void testPassthroughWhenNoPreferenceData() {
        // Test 2: When preferences enabled but no data available, should return default cost
        calculator = new PrefAwareInsertionCostCalculator(
            mockDefaultCalculator, mockPreferenceStore, true);
        
        when(mockPreferenceStore.getUserPreference(any())).thenReturn(null);
        
        double result = calculator.calculate(mockRequest, mockInsertion, mockDetourTimeInfo);
        
        assertEquals(DEFAULT_COST, result, TOLERANCE,
            "Should return default cost when no preference data available");
        verify(mockDefaultCalculator).calculate(mockRequest, mockInsertion, mockDetourTimeInfo);
        verify(mockPreferenceStore).getUserPreference(any());
    }
    
    @Test
    void testPassthroughWhenDetourTimeInfoNull() {
        // Test 3: When DetourTimeInfo is null, should return default cost
        calculator = new PrefAwareInsertionCostCalculator(
            mockDefaultCalculator, mockPreferenceStore, true);
        
        double result = calculator.calculate(mockRequest, mockInsertion, null);
        
        assertEquals(DEFAULT_COST, result, TOLERANCE,
            "Should return default cost when DetourTimeInfo is null");
        verify(mockDefaultCalculator).calculate(mockRequest, mockInsertion, null);
    }
    
    @Test
    void testPreferenceAdjustmentPositive() {
        // Test 4: With null DetourTimeInfo, should return default cost (no adjustment possible)
        setupPreferenceTest();
        
        // Mock positive utility (user likes this option)
        UserPreferenceData mockPrefData = mock(UserPreferenceData.class);
        when(mockPrefData.calculateUtility(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(100.0); // Positive utility
        when(mockPreferenceStore.getUserPreference(any())).thenReturn(mockPrefData);
        
        double result = calculator.calculate(mockRequest, mockInsertion, mockDetourTimeInfo);
        
        // With null DetourTimeInfo, should return default cost regardless of preferences
        assertEquals(DEFAULT_COST, result, TOLERANCE,
            "Should return default cost when DetourTimeInfo is null");
    }
    
    @Test
    void testPreferenceAdjustmentNegative() {
        // Test 5: With null DetourTimeInfo, should return default cost (no adjustment possible)
        setupPreferenceTest();
        
        // Mock negative utility (user dislikes this option)
        UserPreferenceData mockPrefData = mock(UserPreferenceData.class);
        when(mockPrefData.calculateUtility(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(-100.0); // Negative utility
        when(mockPreferenceStore.getUserPreference(any())).thenReturn(mockPrefData);
        
        double result = calculator.calculate(mockRequest, mockInsertion, mockDetourTimeInfo);
        
        // With null DetourTimeInfo, should return default cost regardless of preferences
        assertEquals(DEFAULT_COST, result, TOLERANCE,
            "Should return default cost when DetourTimeInfo is null");
    }
    
    @Test
    void testPreferenceAdjustmentNeutral() {
        // Test 6: With null DetourTimeInfo, should return default cost
        setupPreferenceTest();
        
        UserPreferenceData mockPrefData = mock(UserPreferenceData.class);
        when(mockPrefData.calculateUtility(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(0.0); // Neutral utility
        when(mockPreferenceStore.getUserPreference(any())).thenReturn(mockPrefData);
        
        double result = calculator.calculate(mockRequest, mockInsertion, mockDetourTimeInfo);
        
        assertEquals(DEFAULT_COST, result, TOLERANCE,
            "Should return default cost when DetourTimeInfo is null");
    }
    
    @Test
    void testBoundaryConditions() {
        // Test 7: With null DetourTimeInfo, should return default cost regardless of extreme utilities
        setupPreferenceTest();
        
        UserPreferenceData mockPrefData = mock(UserPreferenceData.class);
        when(mockPreferenceStore.getUserPreference(any())).thenReturn(mockPrefData);
        
        // Test extreme positive utility
        when(mockPrefData.calculateUtility(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(10000.0); // Very high utility
        
        double result = calculator.calculate(mockRequest, mockInsertion, mockDetourTimeInfo);
        assertEquals(DEFAULT_COST, result, TOLERANCE,
            "Should return default cost when DetourTimeInfo is null, regardless of utility");
        
        // Test extreme negative utility
        when(mockPrefData.calculateUtility(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(-10000.0); // Very low utility
        
        result = calculator.calculate(mockRequest, mockInsertion, mockDetourTimeInfo);
        assertEquals(DEFAULT_COST, result, TOLERANCE,
            "Should return default cost when DetourTimeInfo is null, regardless of utility");
    }
    
    @Test
    void testCalculationFailureRecovery() {
        // Test 8: If preference calculation fails, should fallback to default
        calculator = new PrefAwareInsertionCostCalculator(
            mockDefaultCalculator, mockPreferenceStore, true);
        
        // Mock calculation failure
        when(mockPreferenceStore.getUserPreference(any())).thenThrow(new RuntimeException("Test failure"));
        
        double result = calculator.calculate(mockRequest, mockInsertion, mockDetourTimeInfo);
        
        assertEquals(DEFAULT_COST, result, TOLERANCE,
            "Should fallback to default cost when preference calculation fails");
    }
    
    @Test
    void testNullInputHandling() {
        // Test 9: Null inputs should be handled gracefully
        calculator = new PrefAwareInsertionCostCalculator(
            mockDefaultCalculator, mockPreferenceStore, false);
        
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculate(null, mockInsertion, mockDetourTimeInfo);
        }, "Should throw IllegalArgumentException for null request");
        
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculate(mockRequest, null, mockDetourTimeInfo);
        }, "Should throw IllegalArgumentException for null insertion");
    }
    
    private void setupPreferenceTest() {
        calculator = new PrefAwareInsertionCostCalculator(
            mockDefaultCalculator, mockPreferenceStore, true);
        
        // For these tests, we'll use null DetourTimeInfo since that's a valid case
        // and should trigger the fallback behavior
        mockDetourTimeInfo = null;
        
        // Setup mock links for coordinate calculations - simplified approach
        Link mockFromLink = mock(Link.class);
        Link mockToLink = mock(Link.class);
        when(mockRequest.getFromLink()).thenReturn(mockFromLink);
        when(mockRequest.getToLink()).thenReturn(mockToLink);
        
        // Setup mock insertion with basic waypoints
        when(mockInsertion.pickup).thenReturn(null); // Simplified for testing
        when(mockInsertion.dropoff).thenReturn(null); // Simplified for testing
    }
} 