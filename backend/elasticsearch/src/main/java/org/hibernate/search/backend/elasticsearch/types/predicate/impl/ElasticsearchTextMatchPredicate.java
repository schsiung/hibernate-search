/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.types.predicate.impl;

import java.lang.invoke.MethodHandles;

import org.hibernate.search.backend.elasticsearch.gson.impl.JsonAccessor;
import org.hibernate.search.backend.elasticsearch.logging.impl.Log;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.AnalyzerConstants;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.DataTypes;
import org.hibernate.search.backend.elasticsearch.search.impl.ElasticsearchSearchContext;
import org.hibernate.search.backend.elasticsearch.search.impl.ElasticsearchSearchFieldContext;
import org.hibernate.search.backend.elasticsearch.search.predicate.impl.PredicateRequestContext;
import org.hibernate.search.backend.elasticsearch.types.codec.impl.ElasticsearchFieldCodec;
import org.hibernate.search.engine.reporting.spi.EventContexts;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

import com.google.gson.JsonObject;

class ElasticsearchTextMatchPredicate extends ElasticsearchStandardMatchPredicate {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private static final JsonAccessor<Integer> FUZZINESS_ACCESSOR = JsonAccessor.root().property( "fuzziness" ).asInteger();
	private static final JsonAccessor<Integer> PREFIX_LENGTH_ACCESSOR = JsonAccessor.root().property( "prefix_length" ).asInteger();
	private static final JsonAccessor<String> ANALYZER_ACCESSOR = JsonAccessor.root().property( "analyzer" ).asString();

	private final Integer fuzziness;
	private final Integer prefixLength;
	private final String analyzer;

	ElasticsearchTextMatchPredicate(Builder builder) {
		super( builder );
		fuzziness = builder.fuzziness;
		prefixLength = builder.prefixLength;
		analyzer = builder.analyzer;
	}

	@Override
	protected JsonObject doToJsonQuery(PredicateRequestContext context, JsonObject outerObject,
			JsonObject innerObject) {
		if ( fuzziness != null ) {
			FUZZINESS_ACCESSOR.set( innerObject, fuzziness );
		}
		if ( analyzer != null ) {
			ANALYZER_ACCESSOR.set( innerObject, analyzer );
		}
		if ( prefixLength != null ) {
			PREFIX_LENGTH_ACCESSOR.set( innerObject, prefixLength );
		}
		return super.doToJsonQuery( context, outerObject, innerObject );
	}

	static class Builder extends ElasticsearchStandardMatchPredicate.Builder<String> {
		private final String type;

		private Integer fuzziness;
		private Integer prefixLength;
		private String analyzer;

		Builder(ElasticsearchSearchContext searchContext, ElasticsearchSearchFieldContext<String> field,
				ElasticsearchFieldCodec<String> codec, String type) {
			super( searchContext, field, codec );
			this.type = type;
		}

		@Override
		public void fuzzy(int maxEditDistance, int exactPrefixLength) {
			this.fuzziness = maxEditDistance;
			this.prefixLength = exactPrefixLength;
		}

		@Override
		public void analyzer(String analyzerName) {
			this.analyzer = analyzerName;
		}

		@Override
		public void skipAnalysis() {
			if ( DataTypes.KEYWORD.equals( type ) ) {
				throw log.skipAnalysisOnKeywordField( absoluteFieldPath, EventContexts.fromIndexFieldAbsolutePath( absoluteFieldPath ) );
			}

			analyzer( AnalyzerConstants.KEYWORD_ANALYZER );
		}

		@Override
		public SearchPredicate build() {
			if ( analyzer == null ) {
				// Check analyzer compatibility for multi-index search
				field.type().searchAnalyzerName();
				field.type().normalizerName();
			}

			return new ElasticsearchTextMatchPredicate( this );
		}
	}
}
