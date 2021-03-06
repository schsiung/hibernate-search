/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.tck.testsupport.types.values;

import static java.util.Arrays.asList;

import java.util.Collections;
import java.util.List;

public abstract class IndexableValues<F> {

	private final List<F> single = Collections.unmodifiableList( createSingle() );

	private final List<List<F>> multi = Collections.unmodifiableList( makeMulti( single ) );

	public List<F> getSingle() {
		return single;
	}

	public List<List<F>> getMulti() {
		return multi;
	}

	protected abstract List<F> createSingle();

	protected static <T> List<List<T>> makeMulti(List<T> single) {
		return asList(
				asList( single.get( 0 ), single.get( 1 ), single.get( 2 ) ),
				asList( single.get( 2 ), single.get( 1 ), single.get( 0 ) ),
				asList( single.get( 0 ), single.get( 0 ), single.get( 0 ) )
		);
	}
}
