package com.example.campaignbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Service
public class TelegramMessageService {

    private final RestTemplate restTemplate;
    private final String botToken;

    public TelegramMessageService(
            RestTemplate restTemplate,
            @Value("${telegram.bot.token}") String botToken) {

        this.restTemplate = restTemplate;
        this.botToken = botToken;
    }

    public String sendMessage(long chatId, String message) {

        String url = UriComponentsBuilder
                .fromUriString("https://api.telegram.org/bot{token}/sendMessage")
                .buildAndExpand(botToken)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("chat_id", String.valueOf(chatId));
        body.add("text", message);
        body.add("parse_mode", "HTML");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url,
                request,
                String.class);

        return response.getBody();
    }
}