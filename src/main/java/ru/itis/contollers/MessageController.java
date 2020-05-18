package ru.itis.contollers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import itis.MessageDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.TextMessage;
import ru.itis.dto.MessageStatus;
import ru.itis.dto.QueueDto;
import ru.itis.services.impl.JavaLabMessageQueue;
import ru.itis.services.interfaces.MessageService;
import ru.itis.services.interfaces.QueueService;

@Controller
public class MessageController {
    @Autowired
    JavaLabMessageQueue javaLabMessageQueue;
    @Autowired
    MessageService messageService;
    @Autowired
    QueueService queueService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    private SimpMessagingTemplate template;

    @MessageMapping("/{queue}")
    public void getMessage(Message<String> message, @DestinationVariable("queue") String queueName) {
        System.out.println("getMessage " + message);
        MessageDto messageDto = null;
        try {
            messageDto = objectMapper.readValue(message.getPayload(), MessageDto.class);

            System.out.println(messageDto);
            MessageDto messageFromQueue = javaLabMessageQueue.putMessage(queueName, messageDto);
            QueueDto queueDto = queueService.findByName(queueName);

            messageService.save(ru.itis.dto.MessageDto.builder()
                    .messageId(messageFromQueue.getMessageId())
                    .status(MessageStatus.valueOf(messageFromQueue.getStatus().getTitle()))
                    .queueName(queueDto.getName())
                    .body(messageDto.getBody().toString())
                    .build());

            if (javaLabMessageQueue.getLastMessage(queueName) == null) {
                MessageDto newMessage = javaLabMessageQueue.getNewMessage(queueName);
                template.convertAndSend("/topic/" + queueName, new TextMessage(objectMapper.writeValueAsString(newMessage)));
                System.out.println("message sent to /topic/" +  queueName);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @MessageMapping("/update/{queue}")
    public void update(Message<String> message, @DestinationVariable("queue") String queueName) throws JsonProcessingException {
        System.out.println("update");
        MessageDto messageDto = objectMapper.readValue(message.getPayload(), MessageDto.class);
        MessageDto messageFromQueue = javaLabMessageQueue.update(messageDto, queueName);

        ru.itis.dto.MessageDto foundMessage = messageService.findByMessageId(messageDto.getHeaders().get("messageId"));
        foundMessage.setStatus(MessageStatus.valueOf(messageDto.getBody().toString()));
        System.out.println("foundMessage " + foundMessage);
        messageService.update(foundMessage);

        if (messageFromQueue == null) {
            MessageDto newMessage = javaLabMessageQueue.getNewMessage(queueName);
            template.convertAndSend("/topic/" + queueName, new TextMessage(objectMapper.writeValueAsString(newMessage)));
            System.out.println("mes sent to consumer. queueName: " + queueName);
        }
    }

}
