package ru.itis.services.impl;


import itis.MessageDto;
import itis.MessageDto.Status;
import lombok.Data;
import org.springframework.stereotype.Service;
import ru.itis.dto.ErrorDto;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

import static itis.MessageDto.Status.COMPLETED;
import static itis.MessageDto.Status.NEW;

@Service
public class JavaLabMessageQueue {
    private Map<String, QueueObject> queues;

    public JavaLabMessageQueue() {
        this.queues = new HashMap<>();
    }

    public void createQueue(String name) {
        queues.put(name, new QueueObject());
        System.out.println("queue created name: " + name);
    }

    public MessageDto update(MessageDto originalMess, String queueName) {
        MessageDto res = null;

        MessageDto lastMessage = getLastMessage(queueName);

        if (lastMessage == null) {
            res = getError("there are no tasks at the moment");
        } else {
            Status status = Status.valueOf((String) originalMess.getBody());
            if (status == COMPLETED) {
                setLastMessage(queueName, null);
                System.out.println(getLastMessage(queueName));
            } else {
                lastMessage.setStatus(status);
            }
            System.out.println("updated mes: " + lastMessage);
        }
        System.out.println(queues.get(queueName));
        return res;
    }

    public MessageDto getNewMessage(String queueName) {
        MessageDto res;

        QueueObject queueObject = queues.get(queueName);
        MessageDto lastMessage = getLastMessage(queueName);

        if (lastMessage == null && queueObject.queue.size() == 0) {
            res = getError("there are no tasks yet");
        } else {
            if (lastMessage != null) {
                if (lastMessage.getStatus() == NEW) {
                    res = lastMessage;
                } else {
                    res = getError("please finish last task first");
                }
            } else {
                res = new MessageDto();

                MessageDto polledMessage = queueObject.getQueue().poll();
                System.out.println("polledMessage " + polledMessage);
                setLastMessage(queueName, polledMessage);

                Object polledMessageBody = polledMessage.getBody();
                res.setBody(polledMessageBody);
                res.setMessageId(polledMessage.getMessageId());
                res.setStatus(polledMessage.getStatus());

                Map<String, String> headers = new HashMap<>();
                headers.put("command", "receive");
                headers.put("queueName", queueName);
                headers.put("messageId", polledMessage.getMessageId());
                res.setHeaders(headers);

                System.out.println("got new message");
            }
        }
        return res;
    }

    public MessageDto putMessage(String queueName, MessageDto message) {
        String messageId = UUID.randomUUID().toString();
        message.setMessageId(messageId);

        queues.get(queueName).getQueue().add(message);
        System.out.println("message in queue: " + queues.get(queueName).getQueue());
        return message;
    }

    private void setLastMessage(String queueName, MessageDto message) {
        queues.get(queueName).setLastMessage(message);
    }

    public MessageDto getLastMessage(String queueName) {
        QueueObject queueObject = queues.get(queueName);
        return queueObject.getLastMessage();
    }

    private MessageDto getError(String descr) {
        MessageDto res = new MessageDto();
        Map<String, String> headers = new HashMap<>();
        headers.put("command", "error");
        res.setHeaders(headers);

        ErrorDto errorDto = new ErrorDto(descr);
        res.setBody(errorDto);
        return res;
    }

    @Data
    class QueueObject {
        private Queue<MessageDto> queue;
        private MessageDto lastMessage;

        public QueueObject() {
            queue = new ArrayBlockingQueue<>(10);
        }
    }
}
