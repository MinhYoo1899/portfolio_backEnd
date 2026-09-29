package egovframework.com.config;

import java.io.IOException;

import egovframework.com.cmm.util.EgovBasicLogger;
import egovframework.eeum.base.crypto.FieldEncryptor;
import egovframework.eeum.base.crypto.GuestMblTelnoEncryptedTypeHandler;
import egovframework.eeum.base.crypto.MblTelnoEncryptedTypeHandler;
import egovframework.eeum.base.crypto.PicMblTelnoEncryptedTypeHandler;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.support.lob.DefaultLobHandler;

/**
 * @ClassName : EgovConfigAppMapper.java
 * @Description : Mapper 설정
 *
 * @author : 윤주호
 * @since  : 2021. 7. 20
 * @version : 1.0
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일              수정자               수정내용
 *  -------------  ------------   ---------------------
 *   2021. 7. 20    윤주호               최초 생성
 * </pre>
 *
 */
@Configuration
@PropertySources({
	@PropertySource("classpath:/application.properties")
})
// 이음: 도메인 매퍼 인터페이스(@Mapper, 각 업무 service/{서비스}/dao) 스캔 — XML 은 namespace=FQCN 으로 바인딩
@org.mybatis.spring.annotation.MapperScan(
	basePackages = "egovframework.eeum",
	annotationClass = org.apache.ibatis.annotations.Mapper.class,
	sqlSessionFactoryRef = "sqlSession")
public class EgovConfigAppMapper {

    private final DataSource dataSource;

    private final Environment env;

    public EgovConfigAppMapper(DataSource dataSource, Environment env) {
        this.dataSource = dataSource;
        this.env = env;
    }

	private String dbType;

	@PostConstruct
	void init() {
		dbType = env.getProperty("Globals.DbType");
	}

	@Bean
	@Lazy
	public DefaultLobHandler lobHandler() {
		return new DefaultLobHandler();
	}

	@Bean(name = {"sqlSession", "egov.sqlSession"})
	public SqlSessionFactoryBean sqlSession(FieldEncryptor fieldEncryptor) {
		SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
		sqlSessionFactoryBean.setDataSource(dataSource);
		// 이음(Phase B): 암호화 컬럼용 TypeHandler 를 "인스턴스로" 등록 — FieldEncryptor(Spring 빈) 주입본을 사용.
		// XML 에서 typeHandler=클래스 로 명시 지정한 컬럼/파라미터에만 적용(@MappedTypes 미부착 → 일반 String 무영향).
		sqlSessionFactoryBean.setTypeHandlers(new PicMblTelnoEncryptedTypeHandler(fieldEncryptor),
			new GuestMblTelnoEncryptedTypeHandler(fieldEncryptor),
			new MblTelnoEncryptedTypeHandler(fieldEncryptor));

		PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver();

		sqlSessionFactoryBean.setConfigLocation(
			pathMatchingResourcePatternResolver
				.getResource("classpath:/egovframework/mapper/config/mapper-config.xml"));

		try {
			// 이음: 데모 경로(mapper/let) 대신 전체 mapper 하위를 classpath* 로 스캔(우리 매퍼 mapper/eeum 포함, 0개도 허용)
			sqlSessionFactoryBean.setMapperLocations(
				pathMatchingResourcePatternResolver
					.getResources("classpath*:/egovframework/mapper/**/*_" + dbType + ".xml"));
		} catch (IOException e) {
			// 26.03.04 KISA 보안취약점 조치
			// 구체적인 Exception 명시
			EgovBasicLogger.debug("Mapper 파일 로딩 중 오류가 발생했습니다.", e);
			throw new IllegalStateException("Mapper 파일 로딩 중 오류가 발생했습니다.", e);
		}

		return sqlSessionFactoryBean;
	}

	@Bean
	public SqlSessionTemplate egovSqlSessionTemplate(@Qualifier("sqlSession") SqlSessionFactory sqlSession) {
		SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSession);
		return sqlSessionTemplate;
	}
}
