/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.search.query.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.hibernate.search.backend.elasticsearch.search.impl.ElasticsearchSearchContext;
import org.hibernate.search.backend.elasticsearch.search.impl.ElasticsearchSearchQueryElementCollector;
import org.hibernate.search.backend.elasticsearch.search.projection.impl.ElasticsearchCompositeListProjection;
import org.hibernate.search.backend.elasticsearch.search.projection.impl.ElasticsearchSearchProjection;
import org.hibernate.search.backend.elasticsearch.search.projection.impl.ElasticsearchSearchProjectionBuilderFactory;
import org.hibernate.search.engine.backend.session.spi.BackendSessionContext;
import org.hibernate.search.engine.search.projection.SearchProjection;
import org.hibernate.search.engine.search.loading.context.spi.LoadingContextBuilder;
import org.hibernate.search.engine.search.query.spi.SearchQueryBuilderFactory;

public class ElasticsearchSearchQueryBuilderFactory
		implements SearchQueryBuilderFactory<ElasticsearchSearchQueryElementCollector> {

	private final SearchBackendContext searchBackendContext;

	private final ElasticsearchSearchContext searchContext;

	private final ElasticsearchSearchProjectionBuilderFactory searchProjectionFactory;

	public ElasticsearchSearchQueryBuilderFactory(SearchBackendContext searchBackendContext, ElasticsearchSearchContext searchContext,
			ElasticsearchSearchProjectionBuilderFactory searchProjectionFactory) {
		this.searchBackendContext = searchBackendContext;
		this.searchContext = searchContext;
		this.searchProjectionFactory = searchProjectionFactory;
	}

	@Override
	public <E> ElasticsearchSearchQueryBuilder<E> selectEntity(
			BackendSessionContext sessionContext, LoadingContextBuilder<?, E, ?> loadingContextBuilder) {
		return select(
				sessionContext, loadingContextBuilder,
				searchProjectionFactory.<E>entity().build()
		);
	}

	@Override
	public <R> ElasticsearchSearchQueryBuilder<R> selectEntityReference(
			BackendSessionContext sessionContext, LoadingContextBuilder<R, ?, ?> loadingContextBuilder) {
		return select(
				sessionContext, loadingContextBuilder,
				searchProjectionFactory.<R>entityReference().build()
		);
	}

	@Override
	public <P> ElasticsearchSearchQueryBuilder<P> select(
			BackendSessionContext sessionContext, LoadingContextBuilder<?, ?, ?> loadingContextBuilder,
			SearchProjection<P> projection) {
		return createSearchQueryBuilder(
				sessionContext, loadingContextBuilder,
				searchProjectionFactory.toImplementation( projection )
		);
	}

	@Override
	public ElasticsearchSearchQueryBuilder<List<?>> select(
			BackendSessionContext sessionContext, LoadingContextBuilder<?, ?, ?> loadingContextBuilder,
			SearchProjection<?>... projections) {
		return createSearchQueryBuilder( sessionContext, loadingContextBuilder, createRootProjection( projections ) );
	}

	private ElasticsearchSearchProjection<?, List<?>> createRootProjection(SearchProjection<?>[] projections) {
		List<ElasticsearchSearchProjection<?, ?>> children = new ArrayList<>( projections.length );

		for ( SearchProjection<?> projection : projections ) {
			children.add( searchProjectionFactory.toImplementation( projection ) );
		}

		return new ElasticsearchCompositeListProjection<>( searchContext.indexes().hibernateSearchIndexNames(), Function.identity(), children );
	}

	private <H> ElasticsearchSearchQueryBuilder<H> createSearchQueryBuilder(
			BackendSessionContext sessionContext, LoadingContextBuilder<?, ?, ?> loadingContextBuilder,
			ElasticsearchSearchProjection<?, H> rootProjection) {
		return searchBackendContext.createSearchQueryBuilder(
				searchContext,
				sessionContext,
				loadingContextBuilder, rootProjection
		);
	}
}
