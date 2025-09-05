package com.udacity.catpoint;

import com.udacity.catpoint.image.FakeImageService;
import com.udacity.catpoint.image.ImageService;
import com.udacity.catpoint.security.data.*;
import com.udacity.catpoint.security.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.*;

class AppTest {

    @Mock private SecurityRepository securityRepository;
    @Mock private ImageService imageService;

    private SecurityService securityService;
    private Sensor sensor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        securityService = new SecurityService(securityRepository, imageService);
        sensor = new Sensor("Test Sensor", SensorType.DOOR);

        // sane defaults to avoid NPEs and unintended branches
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
    }

    // 1) If the system is armed and sensor becomes activated
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void changeSensorActivationStatus_systemArmedAndSensorActivated_alarmStatusPending(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);

        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    // 2) If armed and pending
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void changeSensorActivationStatus_systemArmedAndSensorActivatedWithPendingAlarm_alarmStatusAlarm(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // 3) If pendig sensors not active -> NO_ALARM
    @Test
    void changeSensorActivationStatus_pendingAlarmAndAllSensorsInactive_noAlarmStatus() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        Set<Sensor> sensors = new HashSet<>();
        Sensor anotherSensor = new Sensor("Another Sensor", SensorType.WINDOW);
        anotherSensor.setActive(false);
        sensors.add(anotherSensor);
        sensors.add(sensor);

        when(securityRepository.getSensors()).thenReturn(sensors);

        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // 4) If alarm  active
    @Test
    void changeSensorActivationStatus_alarmActive_noAlarmStatusChange() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);

        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, never()).setAlarmStatus(any());
    }

    // 5) Reactivating an already active sensor pendig
    @Test
    void changeSensorActivationStatus_sensorActivatedWhileActiveAndPendingState_noChange() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, never()).setAlarmStatus(any());
    }

    // 6) Deactivating an already inactive sensor
    @Test
    void changeSensorActivationStatus_sensorDeactivatedWhileInactive_noAlarmStatusChange() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);

        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository, never()).setAlarmStatus(any());
    }

    // 7) Cat detected while ARMED_HOME
    @Test
    void processImage_catDetectedAndArmedHome_alarmStatusAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);

        BufferedImage testImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(testImage);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // 8) No cat and no active sensors M
    @Test
    void processImage_noCatDetectedAndNoActiveSensors_alarmStatusNoAlarm() {
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);

        Set<Sensor> sensors = new HashSet<>();
        Sensor s1 = new Sensor("Sensor 1", SensorType.DOOR);
        Sensor s2 = new Sensor("Sensor 2", SensorType.WINDOW);
        s1.setActive(false); s2.setActive(false);
        when(securityRepository.getSensors()).thenReturn(sensors);
        sensors.add(s1); sensors.add(s2);

        BufferedImage testImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(testImage);

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // 9) Disarming
    @Test
    void setArmingStatus_systemDisarmed_alarmStatusNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // 10) Arming (home/away)
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void setArmingStatus_systemArmed_allSensorsInactive(ArmingStatus armingStatus) {
        Set<Sensor> sensors = new HashSet<>();
        Sensor s1 = new Sensor("Sensor 1", SensorType.DOOR); s1.setActive(true);
        Sensor s2 = new Sensor("Sensor 2", SensorType.WINDOW); s2.setActive(true);
        sensors.add(s1); sensors.add(s2);
        when(securityRepository.getSensors()).thenReturn(sensors);

        securityService.setArmingStatus(armingStatus);

        verify(securityRepository, times(2)).updateSensor(any(Sensor.class));
        sensors.forEach(s -> assertFalse(s.getActive()));
    }

    // 11) Arming home while catCurrentlyDetected
    @Test
    void setArmingStatus_armedHomeWithCatDetected_alarmStatusAlarm() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);

        securityService.processImage(img);  
        reset(securityRepository);         

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // 12) No cat but sensors active
    @Test
    void processImage_noCatDetectedButSensorsActive_noAlarmStatusChange() {
        Set<Sensor> sensors = new HashSet<>();
        Sensor activeSensor = new Sensor("Active", SensorType.DOOR);
        activeSensor.setActive(true);
        sensors.add(activeSensor);

        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);
        when(securityRepository.getSensors()).thenReturn(sensors);

        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(img);

        verify(securityRepository, never()).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // 13) Add & remove sensor pass
    @Test
    void addAndRemoveSensor() {
        securityService.addSensor(sensor);
        verify(securityRepository).addSensor(sensor);
        securityService.removeSensor(sensor);
        verify(securityRepository).removeSensor(sensor);
    }

    // 14) changeSensorActivationStatus always updates with the sensor
    @Test
    void changeSensorActivationStatus_triggersUpdate() {
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).updateSensor(sensor);
    }

    @Test
    void processImage_catDetected_setsAlarmStatus() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(eq(image), anyFloat())).thenReturn(true);

        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);

        securityService.processImage(image);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }



    @Test
    void repository_addAndRemoveSensor_updatesStorageCorrectly() {
        PretendDatabaseSecurityRepositoryImpl repo = new PretendDatabaseSecurityRepositoryImpl();
        Sensor s = new Sensor("RepoSensor", SensorType.WINDOW);
        repo.addSensor(s);
        assertTrue(repo.getSensors().contains(s));
        repo.removeSensor(s);
        assertFalse(repo.getSensors().contains(s));
    }

    @Test
    void repository_setArmingAndAlarmStatus_persistsValues() {
        PretendDatabaseSecurityRepositoryImpl repo = new PretendDatabaseSecurityRepositoryImpl();
        repo.setArmingStatus(ArmingStatus.ARMED_AWAY);
        assertEquals(ArmingStatus.ARMED_AWAY, repo.getArmingStatus());
        repo.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        assertEquals(AlarmStatus.PENDING_ALARM, repo.getAlarmStatus());
    }

    @Test
    void fakeImageService_returnsBoolean() {
        FakeImageService fake = new FakeImageService();
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        assertNotNull(fake.imageContainsCat(img, 50.0f));
    }

    @Test
    void sensor_equalsAndHashCode_workCorrectly() {
        Sensor s1 = new Sensor("Door", SensorType.DOOR);
        Sensor s2 = new Sensor("Door", SensorType.DOOR);
        UUID sameId = s1.getSensorId();
        s2.setSensorId(sameId);

        Sensor s3 = new Sensor("Window", SensorType.WINDOW);

        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
        assertNotEquals(s1, s3);
    }

    @Test
    void sensor_compareTo_sortsByName() {
        Sensor s1 = new Sensor("A", SensorType.DOOR);
        Sensor s2 = new Sensor("B", SensorType.WINDOW);
        assertTrue(s1.compareTo(s2) < 0);
        assertTrue(s2.compareTo(s1) > 0);
    }

    @Test
    void setArmingStatus_systemArmed_allSensorsInactive_single() {
        Sensor s1 = new Sensor("Door", SensorType.DOOR);
        s1.setActive(true);
        when(securityRepository.getSensors()).thenReturn(Set.of(s1));

        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);

        verify(securityRepository).updateSensor(s1);
    }
}
//After fixing many issues finally resolved & got build success
//PS C:\Users\KIIT\Desktop\NewPP\cd0384-java-application-deployment-projectstarter-master\starter\catpoint-parent\security-service> mvn clean install
//[INFO] Scanning for projects...
//        [INFO]
//        [INFO] ---------------< com.udacity.catpoint:security-service >----------------
//        [INFO] Building security-service 1.0-SNAPSHOT
//[INFO]   from pom.xml
//[INFO] --------------------------------[ jar ]---------------------------------
//        [WARNING] 3 problems were encountered while building the effective model for com.miglayout:miglayout:jar:3.7.3
//        [INFO]
//        [INFO] --- clean:3.1.0:clean (default-clean) @ security-service ---
//        [INFO] Deleting C:\Users\KIIT\Desktop\NewPP\cd0384-java-application-deployment-projectstarter-master\starter\catpoint-parent\security-service\target
//[INFO]
//        [INFO] --- resources:3.0.2:resources (default-resources) @ security-service ---
//        [INFO] Using 'UTF-8' encoding to copy filtered resources.
//[INFO] Copying 0 resource
//[INFO]
//        [INFO] --- compiler:3.8.0:compile (default-compile) @ security-service ---
//        [INFO] Changes detected - recompiling the module!
//        [INFO] Compiling 16 source files to C:\Users\KIIT\Desktop\NewPP\cd0384-java-application-deployment-projectstarter-master\starter\catpoint-parent\security-service\target\classes
//        [INFO] /C:/Users/KIIT/Desktop/NewPP/cd0384-java-application-deployment-projectstarter-master/starter/catpoint-parent/security-service/src/main/java/com/udacity/catpoint/security/application/SensorPane
//        l.java: C:\Users\KIIT\Desktop\NewPP\cd0384-java-application-deployment-projectstarter-master\starter\catpoint-parent\security-service\src\main\java\com\udacity\catpoint\security\application\SensorPanel.java uses unchecked or unsafe operations.
//        [INFO] /C:/Users/KIIT/Desktop/NewPP/cd0384-java-application-deployment-projectstarter-master/starter/catpoint-parent/security-service/src/main/java/com/udacity/catpoint/security/application/SensorPanel.java: Recompile with -Xlint:unchecked for details.
//        [INFO]
//        [INFO] --- resources:3.0.2:testResources (default-testResources) @ security-service ---
//        [INFO] Using 'UTF-8' encoding to copy filtered resources.
//        [INFO] skip non existing resourceDirectory C:\Users\KIIT\Desktop\NewPP\cd0384-java-application-deployment-projectstarter-master\starter\catpoint-parent\security-service\src\test\resources
//        [INFO]
//        [INFO] --- compiler:3.8.0:testCompile (default-testCompile) @ security-service ---
//        [INFO] Nothing to compile - all classes are up to date
//        [INFO]
//        [INFO] --- surefire:3.0.0-M5:test (default-test) @ security-service ---
//        [INFO] No tests to run.
//        [INFO]
//        [INFO] --- jar:3.2.0:jar (default-jar) @ security-service ---
//        [INFO] Building jar: C:\Users\KIIT\Desktop\NewPP\cd0384-java-application-deployment-projectstarter-master\starter\catpoint-parent\security-service\target\security-service-1.0-SNAPSHOT.jar
//        [INFO]
//        [INFO] --- assembly:3.3.0:single (make-assembly) @ security-service ---
//        [INFO] Building jar: C:\Users\KIIT\Desktop\NewPP\cd0384-java-application-deployment-projectstarter-master\starter\catpoint-parent\security-service\target\security-service-1.0-SNAPSHOT-jar-with-dependencies.jar
//        [INFO]
//        [INFO] --- install:2.5.2:install (default-install) @ security-service ---
//        [INFO] Installing C:\Users\KIIT\Desktop\NewPP\cd0384-java-application-deployment-projectstarter-master\starter\catpoint-parent\security-service\target\security-service-1.0-SNAPSHOT.jar to C:\Users\KIIT\.m2\repository\com\udacity\catpoint\security-service\1.0-SNAPSHOT\security-service-1.0-SNAPSHOT.jar
//        [INFO] Installing C:\Users\KIIT\Desktop\NewPP\cd0384-java-application-deployment-projectstarter-master\starter\catpoint-parent\security-service\pom.xml to C:\Users\KIIT\.m2\repository\com\udacity\catpoint\security-service\1.0-SNAPSHOT\security-service-1.0-SNAPSHOT.pom
//        [INFO] Installing C:\Users\KIIT\Desktop\NewPP\cd0384-java-application-deployment-projectstarter-master\starter\catpoint-parent\security-service\target\security-service-1.0-SNAPSHOT-jar-with-dependencies.jar to C:\Users\KIIT\.m2\repository\com\udacity\catpoint\security-service\1.0-SNAPSHOT\security-service-1.0-SNAPSHOT-jar-with-dependencies.jar
//        [INFO] ------------------------------------------------------------------------
//        [INFO] BUILD SUCCESS
//        [INFO] ------------------------------------------------------------------------
//        [INFO] Total time:  10.898 s
//        [INFO] Finished at: 2025-09-05T21:03:02+05:30
//        [INFO] ------------------------------------------------------------------------
