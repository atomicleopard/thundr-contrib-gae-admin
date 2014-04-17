package com.threewks.thundr.gae.admin;

import com.threewks.thundr.action.method.MethodAction;
import com.threewks.thundr.gae.GaeModule;
import com.threewks.thundr.handlebars.HandlebarsModule;
import com.threewks.thundr.injection.InjectionContext;
import com.threewks.thundr.injection.Module;
import com.threewks.thundr.injection.UpdatableInjectionContext;
import com.threewks.thundr.module.DependencyRegistry;
import com.threewks.thundr.route.Route;
import com.threewks.thundr.route.RouteType;
import com.threewks.thundr.route.Routes;
import com.threewks.thundr.view.handlebars.HandlebarsView;

public class GaeAdminModule implements Module {

	@Override
	public void requires(DependencyRegistry dependencyRegistry) {
		dependencyRegistry.addDependency(GaeModule.class);
		dependencyRegistry.addDependency(HandlebarsModule.class);

	}

	@Override
	public void initialise(UpdatableInjectionContext injectionContext) {

	}

	@Override
	public void configure(UpdatableInjectionContext injectionContext) {

	}

	@Override
	public void start(UpdatableInjectionContext injectionContext) {
		Routes routes = injectionContext.get(Routes.class);
		routes.addRoute(new Route(RouteType.GET, "/admin/datastore", "GaeAdminDatastoreViewer"), new MethodAction(GaeAdminDatastoreController.class, "view"));
		routes.addRoute(new Route(RouteType.GET, "/admin/datastore/kinds", "GaeAdminDatastoreKinds"), new MethodAction(GaeAdminDatastoreController.class, "kinds"));
		routes.addRoute(new Route(RouteType.GET, "/admin/datastore/kind/{kind}", "GaeAdminDatastoreKind"), new MethodAction(GaeAdminDatastoreController.class, "kind"));
		routes.addRoute(new Route(RouteType.POST, "/admin/datastore/kind/{kind}/query", "GaeAdminDatastoreKindQuery"), new MethodAction(GaeAdminDatastoreController.class, "query"));
	}

	@Override
	public void stop(InjectionContext injectionContext) {

	}
}
