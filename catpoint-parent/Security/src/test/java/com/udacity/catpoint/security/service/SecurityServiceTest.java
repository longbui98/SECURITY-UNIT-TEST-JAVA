package com.udacity.catpoint.security.service;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;
import com.udacity.catpoint.security.data.SensorType;


@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {
	private SecurityService securityService;
	private Sensor sensor;
	
	@Mock
	private ImageService imageService;
	
	@Mock
	private SecurityRepository securityRepository;
	
	@Mock
	private StatusListener statusListener;
	
	@BeforeEach
	void init() {
		securityService = new SecurityService(securityRepository, imageService);
		sensor = new Sensor("TEST SENSOR", SensorType.DOOR);
	}
	
	private Set<Sensor> getSampleSensorsTest(int numberSensor) {
		Set<Sensor> sensors = new HashSet<>();
		String nameRandom = UUID.randomUUID().toString();
		SensorType sensorType = SensorType.DOOR;
		
		for(int i = 1; i <= numberSensor; i++) {
			sensor = new Sensor(nameRandom, sensorType);
			sensors.add(sensor);
		}
		
		sensors.stream().forEach(x -> x.setActive(false));
		return sensors;
	}
	
	// 1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
	@Test
	public void sensorActivated_alarmArmedAndStatusActive_alarmStatusToPending() {
		when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
		when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
		securityService.changeSensorActivationStatus(sensor, true);
		
		verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
	}
	
	// 2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
	@Test
	public void alarmArmedAndSensorActivatedAndSystempPending_alarmStatusToAlarm() {
		when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
		when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
		securityService.changeSensorActivationStatus(sensor, true);
		
		verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
	}
	
	// 3. If pending alarm and all sensors are inactive, return to no alarm state.
	@Test
	public void alarmActiveAndSensorInActive_NoAlarmState() {
		when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
		sensor.setActive(false);
		securityService.changeSensorActivationStatus(sensor);
		
		verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);;
	}
	
	// 4. If alarm is active, change in sensor state should not affect the alarm state.
	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void alarmActive_changeSensorState_NotAffectAlarmState(boolean status) {
		when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
		securityService.changeSensorActivationStatus(sensor, status);
	
		verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
	}
	
	// 5. If a sensor is activated while already active and the system is in pending state, change it to alarm state.
	@Test
	public void sensorActivedWhileActiveAndSystemPending_SensorToAlarmState() {
		when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
		sensor.setActive(true);
		securityService.changeSensorActivationStatus(sensor, true);
		
		verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
	}
	
	// 6. If a sensor is deactivated while already inactive, make no changes to the alarm state.
	@Test
	public void sensorInactiveWhileInActive_NoChangeAlarmState() {
		sensor.setActive(false);
		
		verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
	}
	
	// 7. If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
	@Test
	public void detectCat_ImageServiceDetectCatAndSystemArmed_AlarmStatus() {
        BufferedImage catSampleImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(true);
        securityService.processImage(catSampleImage);
        
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
	}
	
	// 8. If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
	@Test
	public void detectCat_ImageServiceDetectNotCat_ChangeAlarmStatusSensorAreNotActive() {
        BufferedImage catSampleImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(false);
        securityService.changeSensorActivationStatus(sensor, false);
        
        securityService.processImage(catSampleImage);
        
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
	}
	
	// 9. If the system is disarmed, set the status to no alarm.
	@Test
	public void systemDisarmed_StatusToAlarm() {
		securityService.setArmingStatus(ArmingStatus.DISARMED);
		
		verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
	}
	
	// 10. If the system is armed, reset all sensors to inactive.
	@ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
	public void systemArmed_ResetSensorsInactiveStatus(ArmingStatus status) {
		when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
		Set<Sensor> sensors = getSampleSensorsTest(5);
		sensors.stream().forEach(sensor -> sensor.setActive(true));
		when(securityRepository.getSensors()).thenReturn(sensors);
		
		securityService.setArmingStatus(status);
		
		securityService.getSensors().forEach(sensor -> assertFalse(sensor.getActive()));
	}
	
	//11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
	@Test
	public void systemArmedHomeAndCameraShowsCat_AlarmStatusToAlarm() {
        when(securityService.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(true);
        
        BufferedImage catSampleImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(catSampleImage);
        
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
	}
	
	//12. Add and remove sensor and StyleService
	@Test
	public void addAndRemoveSenso() {
		securityService.addSensor(sensor);
		securityService.removeSensor(sensor);
	}
	
	//13. Add and remove Status Listener
	@Test
	public void addAndRemoveStatusListenner() {
		securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);
	}
	
	//14. Actual Alarm Status is Alarm and Actual Arming Status is Disarmed
	@Test
	public void alarmStatusAlarmAndArmingStatusDisarmed() {
		when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
		when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
		
		sensor.setActive(false);
		securityService.changeSensorActivationStatus(sensor);
		verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
	}
	
	//15. If the system is armed-home while the camera does not show a cat, set the alarm status to alarm.
	@Test
	public void systemArmedHomeAndCameraDoesNotShowCat_AlarmStatusToAlarm() {
		when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(true);
        BufferedImage catSampleImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        
        securityService.processImage(catSampleImage);
		securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
		
		verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
	}
}