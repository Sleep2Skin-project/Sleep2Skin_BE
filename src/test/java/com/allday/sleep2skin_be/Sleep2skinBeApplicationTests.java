package com.allday.sleep2skin_be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 전체 컨텍스트가 뜨는지 확인한다. 빈 배선이 깨지면 여기서 먼저 잡힌다.
 *
 * <p>test 프로파일이 H2를 물려주므로 로컬에 MySQL이 없어도 돈다.
 */
@SpringBootTest
@ActiveProfiles("test")
class Sleep2skinBeApplicationTests {

    @Test
    void contextLoads() {
    }

}
