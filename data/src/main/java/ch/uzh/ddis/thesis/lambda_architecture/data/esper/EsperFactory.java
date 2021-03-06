package ch.uzh.ddis.thesis.lambda_architecture.data.esper;

import ch.uzh.ddis.thesis.lambda_architecture.data.SRBench.SRBenchDataTypes;
import ch.uzh.ddis.thesis.lambda_architecture.data.debs.DebsDataTypes;
import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;

import java.util.HashMap;
import java.util.Map;

/**
 * The esper factory defines the types an esper engine may receive. It is used to predefine the types that are
 * then send to esper as maps.
 * The logic here encapsulates the structure of the data sets used for the experiments.
 *
 * @author Nicolas Baer <nicolas.baer@gmail.com>
 */
public class EsperFactory {


    private static final String[] observations = {
            SRBenchDataTypes.Observation.PrecipitationObservation.name(),
            SRBenchDataTypes.Observation.SnowfallObservation.name(),
    };

    private static final String[] observationsDouble = {
            SRBenchDataTypes.Observation.RelativeHumidityObservation.name(),
            SRBenchDataTypes.Observation.TemperatureObservation.name(),
            SRBenchDataTypes.Observation.WindDirectionObservation.name(),
            SRBenchDataTypes.Observation.WindObservation.name(),
            SRBenchDataTypes.Observation.WindSpeedObservation.name(),
            SRBenchDataTypes.Observation.VisibilityObservation.name(),
            SRBenchDataTypes.Observation.PressureObservation.name(),
            SRBenchDataTypes.Observation.RainfallObservation.name(),
    };

    private static final Map<String, String[]> measurementsDouble = new HashMap<String, String[]>(){{
        put(SRBenchDataTypes.Measurement.AirTemperature.name(), new String[] {SRBenchDataTypes.Observation.TemperatureObservation.name()});
        put(SRBenchDataTypes.Measurement.DewPoint.name(), new String[] {SRBenchDataTypes.Observation.TemperatureObservation.name()});
        put(SRBenchDataTypes.Measurement.PeakWindDirection.name(), new String[] {SRBenchDataTypes.Observation.WindDirectionObservation.name(), SRBenchDataTypes.Observation.WindObservation.name()});
        put(SRBenchDataTypes.Measurement.PeakWindSpeed.name(), new String[] {SRBenchDataTypes.Observation.WindDirectionObservation.name(), SRBenchDataTypes.Observation.WindObservation.name()});
        put(SRBenchDataTypes.Measurement.Pressure.name(), new String[] {SRBenchDataTypes.Observation.PressureObservation.name()});
        put(SRBenchDataTypes.Measurement.RelativeHumidity.name(), new String[] {SRBenchDataTypes.Observation.TemperatureObservation.name()});
        put(SRBenchDataTypes.Measurement.SoilTemperature.name(), new String[] {SRBenchDataTypes.Observation.TemperatureObservation.name()});
        put(SRBenchDataTypes.Measurement.Visibility.name(), new String[] {SRBenchDataTypes.Observation.VisibilityObservation.name()});
        put(SRBenchDataTypes.Measurement.WaterTemperature.name(), new String[] {SRBenchDataTypes.Observation.TemperatureObservation.name()});
        put(SRBenchDataTypes.Measurement.WindDirection.name(), new String[] {SRBenchDataTypes.Observation.WindDirectionObservation.name(), SRBenchDataTypes.Observation.WindObservation.name()});
        put(SRBenchDataTypes.Measurement.WindGust.name(), new String[] {SRBenchDataTypes.Observation.WindSpeedObservation.name(), SRBenchDataTypes.Observation.WindObservation.name()});
        put(SRBenchDataTypes.Measurement.WindSpeed.name(), new String[] {SRBenchDataTypes.Observation.WindSpeedObservation.name(), SRBenchDataTypes.Observation.WindObservation.name()});
    }};



    private static final Map<String, String[]> measurementsBoolean = new HashMap<String, String[]>(){{
        put(SRBenchDataTypes.Measurement.Precipitation.name(), new String[] {SRBenchDataTypes.Observation.PrecipitationObservation.name(), SRBenchDataTypes.Observation.RainfallObservation.name()});
        put(SRBenchDataTypes.Measurement.PrecipitationAccumulated.name(), new String[] {SRBenchDataTypes.Observation.PrecipitationObservation.name(), SRBenchDataTypes.Observation.RainfallObservation.name()});
        put(SRBenchDataTypes.Measurement.PrecipitationSmoothed.name(), new String[] {SRBenchDataTypes.Observation.PrecipitationObservation.name(), SRBenchDataTypes.Observation.RainfallObservation.name()});
        put(SRBenchDataTypes.Measurement.SnowDepth.name(), new String[] {SRBenchDataTypes.Observation.SnowfallObservation.name(), SRBenchDataTypes.Observation.PrecipitationObservation.name()});
        put(SRBenchDataTypes.Measurement.SnowInterval.name(), new String[] {SRBenchDataTypes.Observation.SnowfallObservation.name(), SRBenchDataTypes.Observation.PrecipitationObservation.name()});
        put(SRBenchDataTypes.Measurement.SnowSmoothed.name(), new String[] {SRBenchDataTypes.Observation.SnowfallObservation.name(), SRBenchDataTypes.Observation.PrecipitationObservation.name()});
        put(SRBenchDataTypes.Measurement.SoilMoisture.name(), new String[] {SRBenchDataTypes.Observation.PrecipitationObservation.name()});
        put(SRBenchDataTypes.Measurement.SoilMoistureTension.name(), new String[] {SRBenchDataTypes.Observation.PrecipitationObservation.name()});
    }};


    /**
     * Generates an esper service provider for the srbench dataset. The provider
     * is aware of all observations and measurement types defined in the `SRBenchDataTypes`
     * and its respective inheritance.
     *
     * @param name name of the esper service
     * @return esper service
     */
    public static EPServiceProvider makeEsperServiceProviderSRBench(String name){
        Configuration config = new Configuration();

        Map<String, Object> supertype = new HashMap<>();
        supertype.put("timestamp", Long.class);
        supertype.put("station", String.class);
        supertype.put("measurement", String.class);
        supertype.put("value", Object.class);
        supertype.put("unit", String.class);
        supertype.put("observation", String.class);
        supertype.put("id", String.class);

        Map<String, Object> doubleMeasurement = new HashMap<>(supertype);
        doubleMeasurement.put("value", Double.class);

        Map<String, Object> booleanMeasurement = new HashMap<>(supertype);
        booleanMeasurement.put("value", Boolean.class);

        config.addEventType(SRBenchDataTypes.srBench, supertype);

        for(String observation : observations){
            config.addEventType(observation, supertype, new String[] {SRBenchDataTypes.srBench});
        }

        for(String observation : observationsDouble){
            config.addEventType(observation, doubleMeasurement, new String[] {SRBenchDataTypes.srBench});
        }


        for(Map.Entry<String, String[]> entry : measurementsDouble.entrySet()){
            config.addEventType(entry.getKey(), doubleMeasurement, entry.getValue());
        }


        for(Map.Entry<String, String[]> entry : measurementsBoolean.entrySet()){
            config.addEventType(entry.getKey(), booleanMeasurement, entry.getValue());
        }

        // enables esper to work on timestamp of event instead of system time
        config.getEngineDefaults().getThreading().setInternalTimerEnabled(false);

        EPServiceProvider cep = EPServiceProviderManager.getProvider(name, config);

        return cep;
    }


    /**
     * Generates an esper service provider for the debs dataset. The provider is aware of three types:
     * - the supertype Debs
     * - the measurement type Work
     * - the measurement type Load
     *
     * In addition the esper service provider will work with data time instead of system time.
     *
     * @param name name of the esper service
     * @return initialized esper service provider
     */
    public static EPServiceProvider makeEsperServiceProviderDebs(String name){
        Configuration config = new Configuration();

        Map<String, Object> supertype = new HashMap<>();
        supertype.put("timestamp", Long.class);
        supertype.put("rowId", Long.class);
        supertype.put("value", Double.class);
        supertype.put("type", String.class);
        supertype.put("plugId", Integer.class);
        supertype.put("householdId", Integer.class);
        supertype.put("houseId", Integer.class);

        config.addEventType(DebsDataTypes.Debs, supertype);
        config.addEventType(DebsDataTypes.Measurement.Work.name(), supertype, new String[] {DebsDataTypes.Debs});
        config.addEventType(DebsDataTypes.Measurement.Load.name(), supertype, new String[] {DebsDataTypes.Debs});


        // enables esper to work on timestamp of event instead of system time
        config.getEngineDefaults().getThreading().setInternalTimerEnabled(false);

        EPServiceProvider cep = EPServiceProviderManager.getProvider(name, config);

        return cep;
    }

}
