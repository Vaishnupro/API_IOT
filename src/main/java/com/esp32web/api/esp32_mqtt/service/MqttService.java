package com.esp32web.api.esp32_mqtt.service;

import com.esp32web.api.esp32_mqtt.model.Capteur;
import com.esp32web.api.esp32_mqtt.repository.CapteurRepository;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MqttService {

    private final CapteurRepository capteurRepository;

    // Variable pour stocker la dernière donnée reçue
    private volatile Capteur latestCapteur;

    @Value("${mqtt.broker}")
    private String brokerUrl;

    @Value("${mqtt.topic}")
    private String topic;

    @Value("${mqtt.clientId}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    private MqttClient client;

    public MqttService(CapteurRepository capteurRepository) {
        this.capteurRepository = capteurRepository;
    }

    @PostConstruct
    public void init() {
        System.out.println("mqtt.clientId = " + clientId);
        connect();
    }

    public void connect() {
        try {
            if (client == null) {
                client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            }

            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("🔌 Connexion MQTT perdue : " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    System.out.println("📥 Message reçu : " + payload);

                    try {
                        JSONObject json = new JSONObject(payload);
                        float temperature = (float) json.getDouble("temperature");
                        float humidity = (float) json.getDouble("humidity");
                        int luminositeRaw = json.getInt("luminosite_raw");
                        int humiditeSolRaw = json.getInt("humidite_sol_raw");
                        String macAddress = json.optString("macAddress", null); // Utilise optString pour éviter les erreurs si le champ est absent

                        if (macAddress == null || macAddress.isEmpty()) {
                            throw new IllegalArgumentException("Adresse MAC manquante ou invalide !");
                        }

                        // Log des données extraites
                        System.out.println("Données extraites : temperature=" + temperature +
                                ", humidity=" + humidity +
                                ", luminositeRaw=" + luminositeRaw +
                                ", humiditeSolRaw=" + humiditeSolRaw +
                                ", macAddress=" + macAddress);

                        // Création d'un nouvel objet Capteur avec tous les champs nécessaires
                        Capteur capteur = new Capteur(temperature, humidity, luminositeRaw, humiditeSolRaw, macAddress, null);
                        System.out.println("Objet Capteur créé : " + capteur);

                        // Enregistrement en base
                        Capteur savedCapteur = capteurRepository.save(capteur);
                        System.out.println("✅ Données enregistrées en base avec succès !");

                    } catch (Exception e) {
                        System.out.println("⚠️ Erreur lors du traitement du message : " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    System.out.println("📡 Message MQTT envoyé avec succès.");
                }
            });

            if (!client.isConnected()) {
                client.connect(options);
            }
            client.subscribe(topic);
            System.out.println("✅ Connecté à MQTT et abonné au topic : " + topic);

        } catch (Exception e) {
            System.out.println("❌ Erreur de connexion MQTT : " + e.getMessage());
        }
    }

   // Getter pour récupérer la dernière donnée reçue
   public Capteur getLatestCapteur() { 
       return latestCapteur; 
   }
}
