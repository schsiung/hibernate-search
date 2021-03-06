/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.tck.search.predicate;

import static org.hibernate.search.util.impl.integrationtest.common.assertion.SearchResultAssert.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.search.engine.backend.common.DocumentReference;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.dsl.IndexFieldTypeFactory;
import org.hibernate.search.engine.backend.types.dsl.StandardIndexFieldTypeOptionsStep;
import org.hibernate.search.engine.reporting.spi.EventContexts;
import org.hibernate.search.engine.search.common.BooleanOperator;
import org.hibernate.search.engine.search.predicate.dsl.SimpleQueryFlag;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.integrationtest.backend.tck.testsupport.configuration.DefaultAnalysisDefinitions;
import org.hibernate.search.integrationtest.backend.tck.testsupport.types.FieldModelConsumer;
import org.hibernate.search.integrationtest.backend.tck.testsupport.types.FieldTypeDescriptor;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.StandardFieldMapper;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.ValueWrapper;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.rule.SearchSetupHelper;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.impl.integrationtest.common.FailureReportUtils;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.BulkIndexer;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.SimpleMappedIndex;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingScope;
import org.hibernate.search.util.impl.test.annotation.PortedFromSearch5;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.assertj.core.api.Assertions;

public class SimpleQueryStringSearchPredicateIT {

	private static final String DOCUMENT_1 = "document1";
	private static final String DOCUMENT_2 = "document2";
	private static final String DOCUMENT_3 = "document3";
	private static final String DOCUMENT_4 = "document4";
	private static final String DOCUMENT_5 = "document5";
	private static final String EMPTY = "empty";

	private static final String TERM_1 = "word";
	private static final String TERM_2 = "panda";
	private static final String TERM_3 = "room";
	private static final String TERM_4 = "elephant john";
	private static final String TERM_5 = "crowd";
	private static final String PHRASE_WITH_TERM_2 = "panda breeding";
	private static final String PHRASE_WITH_TERM_4 = "elephant john";
	private static final String PREFIX_FOR_TERM_1_AND_TERM_6 = "wor";
	private static final String PREFIX_FOR_TERM_6 = "worl";
	private static final String PREFIX_FOR_TERM_1_AND_TERM_6_DIFFERENT_CASE = "Wor";
	private static final String PREFIX_FOR_TERM_6_DIFFERENT_CASE = "Worl";
	private static final String TEXT_TERM_1_AND_TERM_2 = "Here I was, feeding my panda, and the crowd had no word.";
	private static final String TEXT_TERM_1_AND_TERM_3 = "Without a word, he went out of the room.";
	private static final String TEXT_TERM_2_IN_PHRASE = "I admired her for her panda breeding expertise.";
	private static final String TEXT_TERM_4_IN_PHRASE_SLOP_2 = "An elephant ran past John.";
	private static final String TEXT_TERM_1_EDIT_DISTANCE_1_OR_TERM_6 = "I came to the world in a dumpster.";

	private static final String COMPATIBLE_INDEX_DOCUMENT_1 = "compatible_1";
	private static final String RAW_FIELD_COMPATIBLE_INDEX_DOCUMENT_1 = "raw_field_compatible_1";
	private static final String INCOMPATIBLE_ANALYZER_INDEX_DOCUMENT_1 = "incompatible_analyzer_1";
	private static final String COMPATIBLE_SEARCH_ANALYZER_INDEX_DOCUMENT_1 = "compatible_search_analyzer_1";

	@Rule
	public final SearchSetupHelper setupHelper = new SearchSetupHelper();

	private final SimpleMappedIndex<IndexBinding> mainIndex =
			SimpleMappedIndex.of( IndexBinding::new ).name( "main" );
	private final SimpleMappedIndex<OtherIndexBinding> compatibleIndex =
			SimpleMappedIndex.of( OtherIndexBinding::createCompatible ).name( "compatible" );
	private final SimpleMappedIndex<OtherIndexBinding> rawFieldCompatibleIndex =
			SimpleMappedIndex.of( OtherIndexBinding::createRawFieldCompatible ).name( "rawFieldCompatible" );
	private final SimpleMappedIndex<OtherIndexBinding> compatibleSearchAnalyzerIndex =
			SimpleMappedIndex.of( OtherIndexBinding::createCompatibleSearchAnalyzer )
					.name( "compatibleSearchAnalyzer" );
	private final SimpleMappedIndex<OtherIndexBinding> incompatibleAnalyzerIndex =
			SimpleMappedIndex.of( OtherIndexBinding::createIncompatibleAnalyzer ).name( "incompatibleAnalyzer" );
	private final SimpleMappedIndex<OtherIndexBinding> unsearchableFieldsIndex =
			SimpleMappedIndex.of( OtherIndexBinding::createUnsearchableFieldsIndexBinding )
					.name( "unsearchableFields" );

	@Before
	public void setup() {
		setupHelper.start()
				.withIndexes(
						mainIndex, compatibleIndex, rawFieldCompatibleIndex, compatibleSearchAnalyzerIndex,
						incompatibleAnalyzerIndex, unsearchableFieldsIndex
				)
				.setup();

		initData();
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2678")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testSimpleQueryString")
	public void booleanOperators() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;
		Function<String, SearchQuery<DocumentReference>> createQuery = queryString -> scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( queryString ) )
				.toQuery();

		assertThat( createQuery.apply( TERM_1 + " " + TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );

		assertThat( createQuery.apply( TERM_1 + " | " + TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );

		assertThat( createQuery.apply( TERM_1 + " + " + TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		assertThat( createQuery.apply( "-" + TERM_1 + " + " + TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_3 );

		assertThat( createQuery.apply( TERM_1 + " + -" + TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_2 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-3847")
	public void booleanOperators_flags() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		// Don't use a whitespace here: there's a bug in ES6.2 that leads the "|",
		// when interpreted as an (empty) term, to be turned into a match-no-docs query.
		String orQueryString = TERM_1 + "|" + TERM_2;
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( orQueryString )
				.defaultOperator( BooleanOperator.AND )
				.flags( SimpleQueryFlag.OR ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( orQueryString )
				.defaultOperator( BooleanOperator.AND )
				.flags( EnumSet.complementOf( EnumSet.of( SimpleQueryFlag.OR ) ) ) )
				.toQuery() )
				// "OR" disabled: "+" is dropped during analysis and we end up with "term1 + term2", since AND is the default operator
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		String andQueryString = TERM_1 + " + " + TERM_2;
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( andQueryString )
				.defaultOperator( BooleanOperator.OR )
				.flags( SimpleQueryFlag.AND ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( andQueryString )
				.defaultOperator( BooleanOperator.OR )
				.flags( EnumSet.complementOf( EnumSet.of( SimpleQueryFlag.AND ) ) ) )
				.toQuery() )
				// "AND" disabled: "+" is dropped during analysis and we end up with "term1 | term2", since OR is the default operator
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );

		String notQueryString = "-" + TERM_1 + " + " + TERM_2;
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( notQueryString )
				.flags( SimpleQueryFlag.AND, SimpleQueryFlag.NOT ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_3 );
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( notQueryString )
				.flags( EnumSet.complementOf( EnumSet.of( SimpleQueryFlag.NOT ) ) ) )
				.toQuery() )
				// "NOT" disabled: "-" is dropped during analysis and we end up with "term1 + term2"
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		// Don't use a whitespace here: there's a bug in ES6.2 that leads the "("/")",
		// when interpreted as an (empty) term, to be turned into a match-no-docs query.
		String precedenceQueryString = TERM_2 + "+(" + TERM_1 + "|" + TERM_3 + ")";
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( precedenceQueryString )
				.flags( SimpleQueryFlag.AND, SimpleQueryFlag.OR, SimpleQueryFlag.PRECEDENCE ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( precedenceQueryString )
				.flags( EnumSet.complementOf( EnumSet.of( SimpleQueryFlag.PRECEDENCE ) ) ) )
				.toQuery() )
				// "PRECENDENCE" disabled: parentheses are dropped during analysis and we end up with "(term2 + term1) | term3"
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-3844") // Used to throw NPE
	public void nonAnalyzedField() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().nonAnalyzedField.relativeFieldName;
		Function<String, SearchQuery<DocumentReference>> createQuery = queryString -> scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( queryString ) )
				.toQuery();

		assertThat( createQuery.apply( TERM_1 + " " + TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_2, DOCUMENT_3 );

		assertThat( createQuery.apply( TERM_1 + " | " + TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_2, DOCUMENT_3 );

		assertThat( createQuery.apply( TERM_1 + " + " + TERM_2 ) )
				.hasNoHits();

		assertThat( createQuery.apply( "-" + TERM_1 + " + " + TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_3 );

		assertThat( createQuery.apply( TERM_1 + " + -" + TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_2 );
	}

	@Test
	public void unsearchable() {
		StubMappingScope scope = unsearchableFieldsIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		Assertions.assertThatThrownBy( () ->
				scope.predicate().simpleQueryString().field( absoluteFieldPath )
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "is not searchable" )
				.hasMessageContaining( "Make sure the field is marked as searchable" )
				.hasMessageContaining( absoluteFieldPath );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2678")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testSimpleQueryString")
	public void defaultOperator() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;
		SearchQuery<DocumentReference> query;

		query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath )
						.matching( TERM_1 + " " + TERM_2 ) )
				.toQuery();
		assertThat( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );

		query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath )
						.matching( TERM_1 + " " + TERM_2 )
						.defaultOperator( BooleanOperator.OR ) )
				.toQuery();
		assertThat( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );

		query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath )
						.matching( TERM_1 + " " + TERM_2 )
						.defaultOperator( BooleanOperator.AND ) )
				.toQuery();
		assertThat( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );
	}

	/**
	 * Check that a simple query string predicate can be used on a field that has a DSL converter.
	 * The DSL converter should be ignored, and there shouldn't be any exception thrown
	 * (the field should be considered as a text field).
	 */
	@Test
	public void withDslConverter() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringFieldWithDslConverter.relativeFieldName;

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( TERM_1 ) )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2700")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testEmptyQueryString")
	public void emptyStringBeforeAnalysis() {
		StubMappingScope scope = mainIndex.createScope();
		MainFieldModel fieldModel = mainIndex.binding().analyzedStringField1;

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.simpleQueryString().field( fieldModel.relativeFieldName ).matching( "" ) )
				.toQuery();

		assertThat( query )
				.hasNoHits();
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2700")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testBlankQueryString")
	public void blankStringBeforeAnalysis() {
		StubMappingScope scope = mainIndex.createScope();
		MainFieldModel fieldModel = mainIndex.binding().analyzedStringField1;

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.simpleQueryString().field( fieldModel.relativeFieldName ).matching( "   " ) )
				.toQuery();

		assertThat( query )
				.hasNoHits();
	}

	@Test
	public void noTokenAfterAnalysis() {
		StubMappingScope scope = mainIndex.createScope();
		MainFieldModel fieldModel = mainIndex.binding().analyzedStringField1;

		SearchQuery<DocumentReference> query = scope.query()
				// Use stopwords, which should be removed by the analysis
				.where( f -> f.simpleQueryString().field( fieldModel.relativeFieldName ).matching( "the a" ) )
				.toQuery();

		assertThat( query )
				.hasNoHits();
	}

	@Test
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testAnalyzer")
	public void analyzerOverride() {
		StubMappingScope scope = mainIndex.createScope();

		String whitespaceAnalyzedField = mainIndex.binding().whitespaceAnalyzedField.relativeFieldName;
		String whitespaceLowercaseAnalyzedField = mainIndex.binding().whitespaceLowercaseAnalyzedField.relativeFieldName;
		String whitespaceLowercaseSearchAnalyzedField = mainIndex.binding().whitespaceLowercaseSearchAnalyzedField.relativeFieldName;

		// Terms are never lower-cased, neither at write nor at query time.
		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.simpleQueryString().field( whitespaceAnalyzedField ).matching( "HERE | PANDA" ) )
				.toQuery();
		assertThat( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_2 );

		// Terms are always lower-cased, both at write and at query time.
		query = scope.query()
				.where( f -> f.simpleQueryString().field( whitespaceLowercaseAnalyzedField ).matching( "HERE | PANDA" ) )
				.toQuery();
		assertThat( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );

		// Terms are lower-cased only at query time. Because we are overriding the analyzer in the predicate.
		query = scope.query()
				.where( f -> f.simpleQueryString().field( whitespaceAnalyzedField ).matching( "HERE | PANDA" )
						.analyzer( DefaultAnalysisDefinitions.ANALYZER_WHITESPACE_LOWERCASE.name ) )
				.toQuery();
		assertThat( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		// Same here. Terms are lower-cased only at query time. Because we've defined a search analyzer.
		query = scope.query()
				.where( f -> f.simpleQueryString().field( whitespaceLowercaseSearchAnalyzedField ).matching( "HERE | PANDA" ) )
				.toQuery();
		assertThat( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		// As for the first query, terms are never lower-cased, neither at write nor at query time.
		// Because even if we've defined a search analyzer, we are overriding it with an analyzer in the predicate,
		// since the overriding takes the precedence over the search analyzer.
		query = scope.query()
				.where( f -> f.simpleQueryString().field( whitespaceLowercaseSearchAnalyzedField ).matching( "HERE | PANDA" )
						.analyzer( DefaultAnalysisDefinitions.ANALYZER_WHITESPACE.name ) )
				.toQuery();
		assertThat( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_2 );
	}

	@Test
	public void analyzerOverride_notExistingName() {
		StubMappingScope scope = mainIndex.createScope();
		String whitespaceAnalyzedField = mainIndex.binding().whitespaceAnalyzedField.relativeFieldName;

		Assertions.assertThatThrownBy( () -> scope.query()
				.where( f -> f.simpleQueryString().field( whitespaceAnalyzedField ).matching( "HERE | PANDA" )
						// we don't have any analyzer with that name
						.analyzer( "this_name_does_actually_not_exist" ) )
				.toQuery().fetchAll()
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "this_name_does_actually_not_exist" );
	}

	@Test
	public void skipAnalysis() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().whitespaceLowercaseAnalyzedField.relativeFieldName;

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( "HERE | PANDA" ) )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );

		// ignoring the analyzer means that the parameter of match predicate will not be tokenized
		// so it will not match any token
		query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( "HERE | PANDA" ).skipAnalysis() )
				.toQuery();

		assertThat( query )
				.hasNoHits();

		// to have a match with the skipAnalysis option enabled, we have to pass the parameter as a token is
		query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( "here" ).skipAnalysis() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );
	}

	@Test
	public void error_unsupportedFieldType() {
		StubMappingScope scope = mainIndex.createScope();

		for ( ByTypeFieldModel fieldModel : mainIndex.binding().unsupportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;

			Assertions.assertThatThrownBy(
					() -> scope.predicate().simpleQueryString().field( absoluteFieldPath ),
					"simpleQueryString() predicate with unsupported type on field " + absoluteFieldPath
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "Text predicates" )
					.hasMessageContaining( "are not supported by" )
					.hasMessageContaining( "'" + absoluteFieldPath + "'" )
					.satisfies( FailureReportUtils.hasContext(
							EventContexts.fromIndexFieldAbsolutePath( absoluteFieldPath )
					) );
		}
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2700")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testNullQueryString")
	public void error_null() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		Assertions.assertThatThrownBy(
				() -> scope.predicate().simpleQueryString().field( absoluteFieldPath ).matching( null ),
				"simpleQueryString() predicate with null value to match"
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Invalid simple query string" )
				.hasMessageContaining( "must be non-null" )
				.hasMessageContaining( absoluteFieldPath );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2678")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testSimpleQueryString")
	public void phrase() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;
		Function<String, SearchQuery<DocumentReference>> createQuery = queryString -> scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( queryString ) )
				.toQuery();

		assertThat( createQuery.apply( "\"" + PHRASE_WITH_TERM_2 + "\"" ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_3 );

		assertThat( createQuery.apply( TERM_3 + " \"" + PHRASE_WITH_TERM_2 + "\"" ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_2, DOCUMENT_3 );

		// Slop
		assertThat( createQuery.apply( "\"" + PHRASE_WITH_TERM_4 + "\"" ) )
				.hasNoHits();
		assertThat( createQuery.apply( "\"" + PHRASE_WITH_TERM_4 + "\"~1" ) )
				.hasNoHits();
		assertThat( createQuery.apply( "\"" + PHRASE_WITH_TERM_4 + "\"~2" ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_4 );
		assertThat( createQuery.apply( "\"" + PHRASE_WITH_TERM_4 + "\"~3" ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_4 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-3847")
	public void phrase_flag() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		assertThat( scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath )
						.matching( "\"" + PHRASE_WITH_TERM_2 + "\"" )
						.flags( SimpleQueryFlag.PHRASE ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_3 );

		assertThat( scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath )
						.matching( "\"" + PHRASE_WITH_TERM_2 + "\"" )
						.flags( EnumSet.complementOf( EnumSet.of( SimpleQueryFlag.PHRASE ) ) ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_3 );

		// Slop
		assertThat( scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath )
						.matching( "\"" + PHRASE_WITH_TERM_4 + "\"~2" )
						.flags( SimpleQueryFlag.PHRASE, SimpleQueryFlag.NEAR ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_4 );

		assertThat( scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath )
						.matching( "\"" + PHRASE_WITH_TERM_4 + "\"~2" )
						.flags( EnumSet.complementOf( EnumSet.of( SimpleQueryFlag.NEAR ) ) ) )
				.toQuery() )
				.hasNoHits();
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2678")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testBoost")
	public void fieldLevelBoost() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath1 = mainIndex.binding().analyzedStringField1.relativeFieldName;
		String absoluteFieldPath2 = mainIndex.binding().analyzedStringField2.relativeFieldName;
		SearchQuery<DocumentReference> query;

		query = scope.query()
				.where( f -> f.simpleQueryString()
						.field( absoluteFieldPath1 ).boost( 5f )
						.field( absoluteFieldPath2 )
						.matching( TERM_3 )
				)
				.sort( f -> f.score() )
				.toQuery();
		assertThat( query )
				.hasDocRefHitsExactOrder( mainIndex.typeName(), DOCUMENT_2, DOCUMENT_1 );

		query = scope.query()
				.where( f -> f.simpleQueryString()
						.field( absoluteFieldPath1 )
						.field( absoluteFieldPath2 ).boost( 5f )
						.matching( TERM_3 )
				)
				.sort( f -> f.score() )
				.toQuery();
		assertThat( query )
				.hasDocRefHitsExactOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );
	}

	@Test
	public void predicateLevelBoost() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath1 = mainIndex.binding().analyzedStringField1.relativeFieldName;
		String absoluteFieldPath2 = mainIndex.binding().analyzedStringField2.relativeFieldName;

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.bool()
						.should( f.simpleQueryString().field( absoluteFieldPath1 )
								.matching( TERM_3 )
						)
						.should( f.simpleQueryString().field( absoluteFieldPath2 )
								.matching( TERM_3 )
								.boost( 7 )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );

		query = scope.query()
				.where( f -> f.bool()
						.should( f.simpleQueryString().field( absoluteFieldPath1 )
								.matching( TERM_3 )
								.boost( 39 )
						)
						.should( f.simpleQueryString().field( absoluteFieldPath2 )
								.matching( TERM_3 )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( mainIndex.typeName(), DOCUMENT_2, DOCUMENT_1 );
	}

	@Test
	public void predicateLevelBoost_withConstantScore() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath1 = mainIndex.binding().analyzedStringField1.relativeFieldName;
		String absoluteFieldPath2 = mainIndex.binding().analyzedStringField2.relativeFieldName;

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.bool()
						.should( f.simpleQueryString().field( absoluteFieldPath1 )
								.matching( TERM_3 )
								.constantScore().boost( 7 )
						)
						.should( f.simpleQueryString().field( absoluteFieldPath2 )
								.matching( TERM_3 )
								.constantScore().boost( 39 )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );

		query = scope.query()
				.where( f -> f.bool()
						.should( f.simpleQueryString().field( absoluteFieldPath1 )
								.matching( TERM_3 )
								.constantScore().boost( 39 )
						)
						.should( f.simpleQueryString().field( absoluteFieldPath2 )
								.matching( TERM_3 )
								.constantScore().boost( 7 )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( mainIndex.typeName(), DOCUMENT_2, DOCUMENT_1 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2678")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testFuzzy")
	public void fuzzy() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;
		Function<String, SearchQuery<DocumentReference>> createQuery = queryString -> scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( queryString ) )
				.toQuery();

		assertThat( createQuery.apply( TERM_1 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );

		assertThat( createQuery.apply( TERM_1 + "~1" ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );

		assertThat( createQuery.apply( TERM_1 + "~2" ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-3847")
	public void fuzzy_flag() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		assertThat( scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath )
						.matching( TERM_1 + "~1" )
						.flags( SimpleQueryFlag.FUZZY ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );

		assertThat( scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath )
						.matching( TERM_1 + "~1" )
						.flags( EnumSet.complementOf( EnumSet.of( SimpleQueryFlag.FUZZY ) ) ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );
	}

	@Test
	public void prefix() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		Function<String, SearchQuery<DocumentReference>> createQuery = queryString -> scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( queryString ) )
				.toQuery();

		assertThat( createQuery.apply( PREFIX_FOR_TERM_1_AND_TERM_6 + "*" ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );

		assertThat( createQuery.apply( PREFIX_FOR_TERM_6 + "*" ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_5 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-3847")
	public void prefix_flag() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		assertThat( scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath )
						.matching( PREFIX_FOR_TERM_1_AND_TERM_6 + "*" )
						.flags( SimpleQueryFlag.PHRASE, SimpleQueryFlag.PREFIX ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );

		assertThat( scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath )
						.matching( PREFIX_FOR_TERM_1_AND_TERM_6 + "*" )
						.flags( EnumSet.complementOf( EnumSet.of( SimpleQueryFlag.PREFIX ) ) ) )
				.toQuery() )
				.hasNoHits();
	}

	@Test
	@TestForIssue(jiraKey = {"HSEARCH-3612", "HSEARCH-3845"})
	public void prefix_normalizePrefixTerm() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		Function<String, SearchQuery<DocumentReference>> createQuery = queryString -> scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( queryString ) )
				.toQuery();

		assertThat( createQuery.apply( PREFIX_FOR_TERM_1_AND_TERM_6_DIFFERENT_CASE + "*" ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );

		assertThat( createQuery.apply( PREFIX_FOR_TERM_6_DIFFERENT_CASE + "*" ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_5 );
	}

	@Test
	public void multiFields() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath1 = mainIndex.binding().analyzedStringField1.relativeFieldName;
		String absoluteFieldPath2 = mainIndex.binding().analyzedStringField2.relativeFieldName;
		String absoluteFieldPath3 = mainIndex.binding().analyzedStringField3.relativeFieldName;
		Function<String, SearchQuery<DocumentReference>> createQuery;

		// field(...)

		createQuery = query -> scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath1 )
						.matching( query )
				)
				.toQuery();

		assertThat( createQuery.apply( TERM_1 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );
		assertThat( createQuery.apply( TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_3 );
		assertThat( createQuery.apply( TERM_3 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_2 );

		// field(...).field(...)

		createQuery = query -> scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath1 )
						.field( absoluteFieldPath2 )
						.matching( query )
				)
				.toQuery();

		assertThat( createQuery.apply( TERM_1 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );
		assertThat( createQuery.apply( TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_3, DOCUMENT_4 );
		assertThat( createQuery.apply( TERM_3 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );

		// field().fields(...)

		createQuery = query -> scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath1 )
						.fields( absoluteFieldPath2, absoluteFieldPath3 )
						.matching( query )
				)
				.toQuery();

		assertThat( createQuery.apply( TERM_1 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );
		assertThat( createQuery.apply( TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_3, DOCUMENT_4, DOCUMENT_5 );
		assertThat( createQuery.apply( TERM_3 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );

		// fields(...)

		createQuery = query -> scope.query()
				.where( f -> f.simpleQueryString().fields( absoluteFieldPath1, absoluteFieldPath2 )
						.matching( query )
				)
				.toQuery();

		assertThat( createQuery.apply( TERM_1 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );
		assertThat( createQuery.apply( TERM_2 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_3, DOCUMENT_4 );
		assertThat( createQuery.apply( TERM_3 ) )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );
	}

	@Test
	public void error_unknownField() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		Assertions.assertThatThrownBy(
				() -> scope.predicate().simpleQueryString().field( "unknown_field" ),
				"simpleQueryString() predicate with unknown field"
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );

		Assertions.assertThatThrownBy(
				() -> scope.predicate().simpleQueryString()
						.fields( absoluteFieldPath, "unknown_field" ),
				"simpleQueryString() predicate with unknown field"
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );

		Assertions.assertThatThrownBy(
				() -> scope.predicate().simpleQueryString().field( absoluteFieldPath )
						.field( "unknown_field" ),
				"simpleQueryString() predicate with unknown field"
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );

		Assertions.assertThatThrownBy(
				() -> scope.predicate().simpleQueryString().field( absoluteFieldPath )
						.fields( "unknown_field" ),
				"simpleQueryString() predicate with unknown field"
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );
	}

	@Test
	public void multiIndex_withCompatibleIndex() {
		StubMappingScope scope = mainIndex.createScope(
				compatibleIndex
		);
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( TERM_1 ) )
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( b -> {
			b.doc( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );
			b.doc( compatibleIndex.typeName(), COMPATIBLE_INDEX_DOCUMENT_1 );
		} );
	}

	@Test
	public void multiIndex_withRawFieldCompatibleIndex() {
		StubMappingScope scope = mainIndex.createScope( rawFieldCompatibleIndex );
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( TERM_1 ) )
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( b -> {
			b.doc( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );
			b.doc( rawFieldCompatibleIndex.typeName(), RAW_FIELD_COMPATIBLE_INDEX_DOCUMENT_1 );
		} );
	}

	@Test
	public void multiIndex_incompatibleAnalyzer() {
		StubMappingScope scope = mainIndex.createScope( incompatibleAnalyzerIndex );
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		Assertions.assertThatThrownBy(
				() -> {
					scope.query()
							.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( TERM_5 ) )
							.toQuery();
				}
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Multiple conflicting types" )
				.hasMessageContaining( absoluteFieldPath )
				.satisfies( FailureReportUtils.hasContext(
						EventContexts.fromIndexNames( mainIndex.name(), incompatibleAnalyzerIndex.name() )
				) )
		;
	}

	@Test
	public void multiIndex_incompatibleAnalyzer_overrideAnalyzer() {
		StubMappingScope scope = mainIndex.createScope( incompatibleAnalyzerIndex );
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( TERM_5 )
						.analyzer( DefaultAnalysisDefinitions.ANALYZER_WHITESPACE_LOWERCASE.name ) )
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( b -> {
			b.doc( mainIndex.typeName(), DOCUMENT_1 );
			b.doc( incompatibleAnalyzerIndex.typeName(), INCOMPATIBLE_ANALYZER_INDEX_DOCUMENT_1 );
		} );
	}

	@Test
	public void multiIndex_incompatibleAnalyzer_searchAnalyzer() {
		StubMappingScope scope = mainIndex.createScope( compatibleSearchAnalyzerIndex );
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( TERM_5 ) )
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( b -> {
			b.doc( mainIndex.typeName(), DOCUMENT_1 );
			b.doc( compatibleSearchAnalyzerIndex.typeName(), COMPATIBLE_SEARCH_ANALYZER_INDEX_DOCUMENT_1 );
		} );
	}

	@Test
	public void multiIndex_incompatibleAnalyzer_skipAnalysis() {
		StubMappingScope scope = mainIndex.createScope( incompatibleAnalyzerIndex );
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.simpleQueryString().field( absoluteFieldPath ).matching( TERM_5 )
						.skipAnalysis() )
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( b -> {
			b.doc( mainIndex.typeName(), DOCUMENT_1 );
			b.doc( incompatibleAnalyzerIndex.typeName(), INCOMPATIBLE_ANALYZER_INDEX_DOCUMENT_1 );
		} );
	}

	@Test
	public void multiIndex_incompatibleSearchable() {
		StubMappingScope scope = mainIndex.createScope( unsearchableFieldsIndex );
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		Assertions.assertThatThrownBy( () -> scope.predicate().simpleQueryString().field( absoluteFieldPath ) )
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Multiple conflicting types" )
				.hasMessageContaining( absoluteFieldPath )
				.satisfies( FailureReportUtils.hasContext(
						EventContexts.fromIndexNames( mainIndex.name(), unsearchableFieldsIndex.name() )
				) )
		;
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-3847")
	public void whitespace() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().nonAnalyzedField.relativeFieldName;

		String whitespaceQueryString = TERM_1 + " " + TERM_2;
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( whitespaceQueryString )
				.flags( SimpleQueryFlag.WHITESPACE ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_2, DOCUMENT_3 );
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( whitespaceQueryString )
				.flags( EnumSet.complementOf( EnumSet.of( SimpleQueryFlag.WHITESPACE ) ) ) )
				.toQuery() )
				// "WHITESPACE" disabled: "term1 term2" is interpreted as a single term and cannot be found
				.hasNoHits();
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-3847")
	public void escape() {
		StubMappingScope scope = mainIndex.createScope();
		String absoluteFieldPath = mainIndex.binding().analyzedStringField1.relativeFieldName;

		String escapedPrefixQueryString = TERM_1 + "\\*";
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( escapedPrefixQueryString )
				.flags( SimpleQueryFlag.AND, SimpleQueryFlag.NOT, SimpleQueryFlag.ESCAPE ) )
				.toQuery() )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );
		assertThat( scope.query().where( f -> f.simpleQueryString().field( absoluteFieldPath )
				.matching( escapedPrefixQueryString )
				.flags( EnumSet.complementOf( EnumSet.of( SimpleQueryFlag.ESCAPE ) ) ) )
				.toQuery() )
				// "ESCAPE" disabled: "\" is interpreted as a literal and the prefix cannot be found
				.hasNoHits();
	}

	private void initData() {
		BulkIndexer mainIndexer = mainIndex.bulkIndexer()
				.add( DOCUMENT_1, document -> {
					document.addValue( mainIndex.binding().nonAnalyzedField.reference, TEXT_TERM_1_AND_TERM_2 );
					document.addValue( mainIndex.binding().analyzedStringField1.reference, TEXT_TERM_1_AND_TERM_2 );
					document.addValue( mainIndex.binding().analyzedStringFieldWithDslConverter.reference, TEXT_TERM_1_AND_TERM_2 );
					document.addValue( mainIndex.binding().analyzedStringField2.reference, TEXT_TERM_1_AND_TERM_3 );
					document.addValue( mainIndex.binding().analyzedStringField3.reference, TERM_4 );
					document.addValue( mainIndex.binding().whitespaceAnalyzedField.reference, TEXT_TERM_1_AND_TERM_2.toLowerCase( Locale.ROOT ) );
					document.addValue( mainIndex.binding().whitespaceLowercaseAnalyzedField.reference, TEXT_TERM_1_AND_TERM_2.toLowerCase( Locale.ROOT ) );
					document.addValue( mainIndex.binding().whitespaceLowercaseSearchAnalyzedField.reference, TEXT_TERM_1_AND_TERM_2.toLowerCase( Locale.ROOT ) );
				} )
				.add( DOCUMENT_2, document -> {
					document.addValue( mainIndex.binding().nonAnalyzedField.reference, TERM_1 );
					document.addValue( mainIndex.binding().analyzedStringField1.reference, TEXT_TERM_1_AND_TERM_3 );
					document.addValue( mainIndex.binding().whitespaceAnalyzedField.reference, TEXT_TERM_1_AND_TERM_2.toUpperCase( Locale.ROOT ) );
					document.addValue( mainIndex.binding().whitespaceLowercaseAnalyzedField.reference, TEXT_TERM_1_AND_TERM_2.toUpperCase( Locale.ROOT ) );
					document.addValue( mainIndex.binding().whitespaceLowercaseSearchAnalyzedField.reference, TEXT_TERM_1_AND_TERM_2.toUpperCase( Locale.ROOT ) );
				} )
				.add( DOCUMENT_3, document -> {
					document.addValue( mainIndex.binding().nonAnalyzedField.reference, TERM_2 );
					document.addValue( mainIndex.binding().analyzedStringField1.reference, TEXT_TERM_2_IN_PHRASE );
					document.addValue( mainIndex.binding().whitespaceAnalyzedField.reference, TEXT_TERM_1_AND_TERM_2 );
					document.addValue( mainIndex.binding().whitespaceLowercaseAnalyzedField.reference, TEXT_TERM_1_AND_TERM_2 );
					document.addValue( mainIndex.binding().whitespaceLowercaseSearchAnalyzedField.reference, TEXT_TERM_1_AND_TERM_2 );
				} )
				.add( DOCUMENT_4, document -> {
					document.addValue( mainIndex.binding().analyzedStringField1.reference, TEXT_TERM_4_IN_PHRASE_SLOP_2 );
					document.addValue( mainIndex.binding().analyzedStringField2.reference, TEXT_TERM_2_IN_PHRASE );
				} )
				.add( DOCUMENT_5, document -> {
					document.addValue( mainIndex.binding().analyzedStringField1.reference, TEXT_TERM_1_EDIT_DISTANCE_1_OR_TERM_6 );
					document.addValue( mainIndex.binding().analyzedStringField3.reference, TEXT_TERM_2_IN_PHRASE );
					document.addValue( mainIndex.binding().analyzedStringField3.reference, TEXT_TERM_1_AND_TERM_3 );
				} )
				.add( EMPTY, document -> { } );
		BulkIndexer compatibleIndexer = compatibleIndex.bulkIndexer()
				.add( COMPATIBLE_INDEX_DOCUMENT_1, document -> {
					document.addValue( compatibleIndex.binding().analyzedStringField1.reference, TEXT_TERM_1_AND_TERM_2 );
				} );
		BulkIndexer rawFieldCompatibleIndexer = rawFieldCompatibleIndex.bulkIndexer()
				.add( RAW_FIELD_COMPATIBLE_INDEX_DOCUMENT_1, document -> {
					document.addValue( rawFieldCompatibleIndex.binding().analyzedStringField1.reference, TEXT_TERM_1_AND_TERM_2 );
				} );
		BulkIndexer incompatibleAnalyzerIndexer = incompatibleAnalyzerIndex.bulkIndexer()
				.add( INCOMPATIBLE_ANALYZER_INDEX_DOCUMENT_1, document -> {
					document.addValue( incompatibleAnalyzerIndex.binding().analyzedStringField1.reference, TEXT_TERM_1_AND_TERM_2 );
				} );
		BulkIndexer compatibleSearchAnalyzerIndexer = compatibleSearchAnalyzerIndex.bulkIndexer()
				.add( COMPATIBLE_SEARCH_ANALYZER_INDEX_DOCUMENT_1, document -> {
					document.addValue( compatibleSearchAnalyzerIndex.binding().analyzedStringField1.reference, TEXT_TERM_1_AND_TERM_2 );
				} );
		mainIndexer.join(
				compatibleIndexer, rawFieldCompatibleIndexer, compatibleSearchAnalyzerIndexer,
				incompatibleAnalyzerIndexer
		);
	}

	private static void forEachTypeDescriptor(Consumer<FieldTypeDescriptor<?>> action) {
		FieldTypeDescriptor.getAll().stream()
				.filter( typeDescriptor -> typeDescriptor.getMatchPredicateExpectations().isPresent() )
				.forEach( action );
	}

	private static void mapByTypeFields(IndexSchemaElement parent, String prefix,
			FieldModelConsumer<Void, ByTypeFieldModel> consumer) {
		forEachTypeDescriptor( typeDescriptor -> {
			ByTypeFieldModel fieldModel = ByTypeFieldModel.mapper( typeDescriptor )
					.map( parent, prefix + typeDescriptor.getUniqueName() );
			consumer.accept( typeDescriptor, null, fieldModel );
		} );
	}

	private static class IndexBinding {
		final List<ByTypeFieldModel> unsupportedFieldModels = new ArrayList<>();

		final MainFieldModel analyzedStringField1;
		final MainFieldModel analyzedStringField2;
		final MainFieldModel analyzedStringField3;
		final MainFieldModel analyzedStringFieldWithDslConverter;
		final MainFieldModel whitespaceAnalyzedField;
		final MainFieldModel whitespaceLowercaseAnalyzedField;
		final MainFieldModel whitespaceLowercaseSearchAnalyzedField;

		final MainFieldModel nonAnalyzedField;

		IndexBinding(IndexSchemaElement root) {
			mapByTypeFields(
					root, "byType_",
					(typeDescriptor, ignored, model) -> {
						if ( !String.class.equals( typeDescriptor.getJavaType() ) ) {
							unsupportedFieldModels.add( model );
						}
					}
			);
			analyzedStringField1 = MainFieldModel.mapper(
					c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD_ENGLISH.name )
			)
					.map( root, "analyzedString1" );
			analyzedStringField2 = MainFieldModel.mapper(
					c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD_ENGLISH.name )
			)
					.map( root, "analyzedString2" );
			analyzedStringField3 = MainFieldModel.mapper(
					c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD_ENGLISH.name )
			)
					.mapMultiValued( root, "analyzedString3" );
			analyzedStringFieldWithDslConverter = MainFieldModel.mapper(
					c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD_ENGLISH.name )
							.dslConverter( ValueWrapper.class, ValueWrapper.toIndexFieldConverter() )
			)
					.map( root, "analyzedStringWithDslConverter" );
			whitespaceAnalyzedField = MainFieldModel.mapper(
					c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_WHITESPACE.name )
			)
					.map( root, "whitespaceAnalyzed" );
			whitespaceLowercaseAnalyzedField = MainFieldModel.mapper(
					c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_WHITESPACE_LOWERCASE.name )
			)
					.map( root, "whitespaceLowercaseAnalyzed" );
			whitespaceLowercaseSearchAnalyzedField = MainFieldModel.mapper(
					c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_WHITESPACE.name )
							.searchAnalyzer( DefaultAnalysisDefinitions.ANALYZER_WHITESPACE_LOWERCASE.name )
			)
					.map( root, "whitespaceLowercaseSearchAnalyzed" );
			// A field without any analyzer or normalizer
			nonAnalyzedField = MainFieldModel.mapper( c -> c.asString() )
					.map( root, "nonAnalyzed" );
		}
	}

	private static class OtherIndexBinding {
		static OtherIndexBinding createCompatible(IndexSchemaElement root) {
			return new OtherIndexBinding(
					MainFieldModel.mapper(
							c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD_ENGLISH.name )
					)
							.map( root, "analyzedString1" )
			);
		}

		static OtherIndexBinding createRawFieldCompatible(IndexSchemaElement root) {
			return new OtherIndexBinding(
					MainFieldModel.mapper(
							c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD_ENGLISH.name )
									// Using a different DSL converter
									.dslConverter( ValueWrapper.class, ValueWrapper.toIndexFieldConverter() )
					)
							.map( root, "analyzedString1" )
			);
		}

		static OtherIndexBinding createIncompatibleAnalyzer(IndexSchemaElement root) {
			return new OtherIndexBinding(
					MainFieldModel.mapper(
							// Using a different analyzer
							c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_WHITESPACE_LOWERCASE.name )
					)
							.map( root, "analyzedString1" )
			);
		}

		static OtherIndexBinding createCompatibleSearchAnalyzer(IndexSchemaElement root) {
			return new OtherIndexBinding(
					MainFieldModel.mapper(
							// Using a different analyzer
							c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_WHITESPACE_LOWERCASE.name )
								// Overriding it with a compatible one
								.searchAnalyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD_ENGLISH.name )
					)
							.map( root, "analyzedString1" )
			);
		}

		static OtherIndexBinding createUnsearchableFieldsIndexBinding(IndexSchemaElement root) {
			return new OtherIndexBinding(
					MainFieldModel.mapper(
							// make the field not searchable
							c -> c.asString().searchable( Searchable.NO )
					)
							.map( root, "analyzedString1" )
			);
		}

		final MainFieldModel analyzedStringField1;

		private OtherIndexBinding(MainFieldModel analyzedStringField1) {
			this.analyzedStringField1 = analyzedStringField1;
		}
	}

	private static class MainFieldModel {
		static StandardFieldMapper<String, MainFieldModel> mapper(
				Function<IndexFieldTypeFactory, StandardIndexFieldTypeOptionsStep<?, String>> configuration) {
			return StandardFieldMapper.of(
					configuration,
					(reference, name) -> new MainFieldModel( reference, name )
			);
		}

		final IndexFieldReference<String> reference;
		final String relativeFieldName;

		private MainFieldModel(IndexFieldReference<String> reference, String relativeFieldName) {
			this.reference = reference;
			this.relativeFieldName = relativeFieldName;
		}
	}

	private static class ByTypeFieldModel {
		static <F> StandardFieldMapper<F, ByTypeFieldModel> mapper(FieldTypeDescriptor<F> typeDescriptor) {
			return StandardFieldMapper.of(
					typeDescriptor::configure,
					(reference, name) -> new ByTypeFieldModel( name )
			);
		}

		final String relativeFieldName;

		private ByTypeFieldModel(String relativeFieldName) {
			this.relativeFieldName = relativeFieldName;
		}
	}
}
