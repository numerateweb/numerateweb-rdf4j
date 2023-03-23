package org.numerateweb.rdf4j;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import net.enilink.komma.core.ILiteralFactory;
import net.enilink.komma.core.KommaModule;
import net.enilink.komma.core.LiteralFactory;
import net.enilink.komma.core.URIs;
import net.enilink.komma.literals.LiteralConverter;

class LiteralConverterModule extends AbstractModule {
	private KommaModule module;

	public LiteralConverterModule(KommaModule module) {
		this.module = module;
	}

	@Override
	protected void configure() {
	}

	@Provides
	@Singleton
	protected ClassLoader provideClassLoader() {
		return module.getClassLoader();
	}

	@Provides
	@Singleton
	protected LiteralConverter provideLiteralConverter(Injector injector) {
		LiteralConverter literalConverter = new LiteralConverter();
		injector.injectMembers(literalConverter);

		module.getDatatypes().forEach(e -> {
			literalConverter.addDatatype(e.getJavaClass(),
					URIs.createURI(e.getRdfType()));
		});

		module.getLiteralMappers().forEach((className, mapper) -> {
			literalConverter.registerMapper(className, mapper);
		});

		return literalConverter;
	}

	@Provides
	@Singleton
	protected ILiteralFactory provideLiteralFactory() {
		return new LiteralFactory();
	}
}