package aptrue.backend.Sse.Service;

import aptrue.backend.Sse.Dto.SseEventWrapper;
import aptrue.backend.Sse.Dto.SseResponseDto.SseResponseDto;
import aptrue.backend.Sse.Repository.SseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseServiceImpl implements SseService {

    private final SseRepository sseRepository;

    public SseEmitter connect(String email) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        sseRepository.save(email, emitter);

        List<Object> cachedEvents = sseRepository.getAllEvents();
        for (Object event : cachedEvents) {
            try {
                emitter.send(event); // 캐시된 이벤트 전송
            } catch (Exception e) {
                emitter.completeWithError(e); // 에러 처리

            }
        }

        emitter.onCompletion(() -> sseRepository.remove(email));
        emitter.onTimeout(() -> sseRepository.remove(email));

        // 첫 연결 시 503에러 방지 위해 더미 데이터 전송
        SseResponseDto data = new SseResponseDto();
        send(data, "연결 성공");




        return emitter;
    }

    public void send(SseResponseDto responseDto, String message) {
        log.info("Sending report to Sse");

        List<Map.Entry<String, SseEmitter>> emittersList = new ArrayList<>(sseRepository.getAll().entrySet());
        Collections.reverse(emittersList);

//        sseRepository.getAll().forEach((key, emitter) -> {
        for (Map.Entry<String, SseEmitter> entry : emittersList) {
            String key = entry.getKey();
            SseEmitter emitter = entry.getValue();

            try {
                log.info("{}", key);
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .name(message) // 이벤트 이름
                        .data(responseDto);
                //.reconnectTime(3000L);
                emitter.send(event); // 데이터 전송
                log.info("zzz{}", responseDto);

                // 캐시된 이벤트도 함께 전송
                List<Object> cachedEvents = sseRepository.getEvents(key); // 해당 키로 캐시된 이벤트 가져오기
//                Collections.reverse(cachedEvents); // 리스트를 역순으로 변환

                for (Object cachedEvent : cachedEvents) {
                    SseEmitter.SseEventBuilder cachedEventBuilder = SseEmitter.event()
                            .name(key) // 캐시된 이벤트 이름 (필요시 변경 가능)
                            .data(cachedEvent);
                    emitter.send(cachedEventBuilder); // 캐시된 이벤트 전송
                    log.info("Cached event sent: {}", cachedEvent);
                }

            } catch (Exception e) {
                log.info("fail");
                emitter.completeWithError(e); // 에러 처리
                sseRepository.remove(key); // 구독 취소

                sseRepository.cacheEvent(key, responseDto);
            }
        };
    }

    @Scheduled(fixedRate = 25000)
    public void sendDummyData() {
        SseResponseDto responseDto = new SseResponseDto();
        send(responseDto, "빈 데이터 전송");
        log.info("Sending dummy data to keep connections alive");
    }
//
//    @Override
//    public SseEmitter connect(String clientId) {
//        SseEmitter emitter = new SseEmitter(TIMEOUT);
//        sseRepository.save(clientId, emitter);
//
//        emitter.onCompletion(() -> sseRepository.remove(clientId));
//        emitter.onTimeout(() -> sseRepository.remove(clientId));
//
//        SseResponseDto responseDto = SseResponseDto.builder()
//                .clipId(0)
//                        .name("전가현이다")
//                                .message("연결 성공")
//                                        .status("연결이다")
//                                                .build();
//
//        sendEvent("연결 성공1", responseDto);
//
////       캐시된 이벤트 전송
//        sseRepository.getCachedEvents(clientId).forEach(eventWrapper -> {
//            try {
//                emitter.send(SseEmitter.event()
//                        .name(eventWrapper.getEventName())
//                        .data(eventWrapper.getData()));
//            } catch (Exception e) {
//                log.error("Failed to send cached event to client {}: {}", clientId, e.getMessage());
//            }
//        });
//        sseRepository.clearCachedEvents(clientId);
//
//        log.info("connect 마지막");
//        return emitter;
//    }
//
//    @Override
//    public void sendEvent(String eventName, SseResponseDto data) {
//
//        log.info("Sending event '{}' to all clients", eventName);
//
//
//        for (Map.Entry<String, SseEmitter> entry : sseRepository.getAllEmitters().entrySet()) {
//            String clientId = entry.getKey();
//            SseEmitter emitter = entry.getValue();
//
//            try {
//                log.info("Sending event222 '{}' to all clients", data.getName());
//                emitter.send(SseEmitter.event()
//                        .name(eventName)
//                        .data(data));
//                log.info("Sending event333 '{}' to all clients", eventName);
//            } catch (Exception e) {
//                log.warn("Failed to send event to client {}: {}", clientId, e.getMessage());
//                sseRepository.cacheEvent(clientId, new SseEventWrapper(eventName, data));
//                sseRepository.remove(clientId);
//            }
//        }
//        log.info("Sending event444 '{}' to all clients", eventName);
//    }
}