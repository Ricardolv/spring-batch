package com.richard.batch;

import lombok.Data;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.RowMapper;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

import javax.sql.DataSource;

@EnableBatchProcessing
@SpringBootApplication
public class SpringBatchApplication {

	@Data
	public static  class  Person {

		private int age;
		private String firstName, email ;

		public Person() {
		}

		public Person(int age, String firstName, String email) {
			this.age = age;
			this.firstName = firstName;
			this.email = email;
		}

		@Override
		public String toString() {
			return "Person{" +
					"age=" + age +
					", firstName='" + firstName + '\'' +
					", email='" + email + '\'' +
					'}';
		}
	}

	@Configuration
	public static class Step1Configuration {

		@Bean
		FlatFileItemReader<Person> fileReader(@Value("${input}") Resource in) throws Exception {
			return new FlatFileItemReaderBuilder<Person>()
					.name("file-reader")
					.resource(in)
					.targetType(Person.class)
					.delimited().delimiter(",").names(new String[]{"firstName", "age", "email"})
					.build();
		}

		@Bean
		JdbcBatchItemWriter<Person> jdbcWriter(DataSource ds) {
			return new JdbcBatchItemWriterBuilder<Person>()
					.dataSource(ds)
					.sql("insert into PEOPLE(AGE, FIRST_NAME, EMAIL) values (:age, :firstName, :email )")
					.beanMapped()
					.build();
		}
	}

	@Configuration
	public static class Step2Configuration {

		@Bean
		ItemReader <Map<Integer, Integer>> jdbcReader(DataSource dataSource) {
			return new JdbcCursorItemReaderBuilder<Map<Integer, Integer>>()
					.dataSource(dataSource)
					.name("jdbc-reader")
					.sql("select COUNT(age) c, age a from PEOPLE group by age")
					.rowMapper((rs, i) -> Collections.singletonMap(rs.getInt("a"), rs.getInt("c")))

					/*.rowMapper(new RowMapper<Map<Integer, Integer>>() {
						@Override
						public Map<Integer, Integer> mapRow(ResultSet resultSet, int i) throws SQLException {
							return Collections.singletonMap(
									resultSet.getInt("a"),
									resultSet.getInt("c")
							);
						}
					})*/

					.build();
		}

		@Bean
		ItemWriter<Map<Integer, Integer>> fileWriter(@Value("${output}") Resource resource) {
			return  new FlatFileItemWriterBuilder<Map<Integer, Integer>>()
					.name("file-writer")
					.resource(resource)
					.lineAggregator(new DelimitedLineAggregator<Map<Integer, Integer>>() {
						{
							setDelimiter(",");
							setFieldExtractor(integerIntegerMap -> {
								Map.Entry<Integer, Integer> next = integerIntegerMap.entrySet().iterator().next();
								return new Object[] {next.getKey(), next.getValue()};
							});
						}
					})

					.build();
		}


	}

	@Bean
	Job job(JobBuilderFactory jbf,
			StepBuilderFactory sbf,
			Step1Configuration step1Configuration,
			Step2Configuration step2Configuration) throws Exception {

		Step s1 = sbf.get("file-db")
				.<Person, Person> chunk(100)
				.reader(step1Configuration.fileReader(null))
				.writer(step1Configuration.jdbcWriter(null))
				.build();

		Step s2 = sbf.get("db-file")
				.<Map<Integer, Integer>, Map<Integer, Integer>> chunk(1000)
				.reader(step2Configuration.jdbcReader(null))
				.writer(step2Configuration.fileWriter(null))
				.build();

		return  jbf.get("etl")
				.incrementer(new RunIdIncrementer())
				.start(s1)
				.next(s2)
				.build();
	}

	public static void main(String[] args) {
		System.setProperty("input", "file://" + new File("/Users/richard/Desktop/in.csv").getAbsolutePath());
		System.setProperty("output", "file://" + new File("/Users/richard/Desktop/out.csv").getAbsolutePath());
		SpringApplication.run(SpringBatchApplication.class, args);
	}
}
