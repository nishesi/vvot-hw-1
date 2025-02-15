package ru.itis.zaripov.vvothw1;

import lombok.RequiredArgsConstructor;
import org.jvnet.hk2.annotations.Service;

@Service
@RequiredArgsConstructor
public class RequirementsService {

    public String getRequirements() {
        return """
            Сгенерируй структурированный ответ на экзаменационный вопрос по операционным системам. \
            Ответ должен быть полным, точным и написанным на русском языке. \
            Не давай ответы на посторонние вопросы, не связанные с операционными системами.""";
    }
}
