/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.sharing.web.internal.portlet;

import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.portlet.MockLiferayPortletRenderRequest;
import com.liferay.portal.kernel.test.portlet.MockLiferayPortletRenderResponse;
import com.liferay.portal.test.rule.LiferayUnitTestRule;
import com.liferay.sharing.web.internal.display.context.ViewSharedAssetsDisplayContext;
import com.liferay.sharing.web.internal.display.context.ViewSharedAssetsDisplayContextFactory;

import jakarta.portlet.RenderRequest;
import jakarta.portlet.RenderResponse;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import org.mockito.Mockito;

/**
 * @author Brian Wulbern
 */
public class SharedAssetsPortletTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Test
	public void testDoView() throws Exception {
		MockLiferayPortletRenderRequest mockLiferayPortletRenderRequest =
			new MockLiferayPortletRenderRequest();

		mockLiferayPortletRenderRequest.setParameter(
			"mvcRenderCommandName", "/shared_assets/view_sharing_entry");

		MockLiferayPortletRenderResponse mockLiferayPortletRenderResponse =
			new MockLiferayPortletRenderResponse();

		ViewSharedAssetsDisplayContext viewSharedAssetsDisplayContext =
			Mockito.mock(ViewSharedAssetsDisplayContext.class);

		ViewSharedAssetsDisplayContextFactory
			viewSharedAssetsDisplayContextFactory = Mockito.mock(
				ViewSharedAssetsDisplayContextFactory.class);

		Mockito.when(
			viewSharedAssetsDisplayContextFactory.
				getViewSharedAssetsDisplayContext(
					mockLiferayPortletRenderRequest,
					mockLiferayPortletRenderResponse)
		).thenReturn(
			viewSharedAssetsDisplayContext
		);

		SharedAssetsPortlet sharedAssetsPortlet = new SharedAssetsPortlet() {

			@Override
			protected void include(
				String path, RenderRequest renderRequest,
				RenderResponse renderResponse) {

				Assert.assertSame(
					viewSharedAssetsDisplayContext,
					renderRequest.getAttribute(
						ViewSharedAssetsDisplayContext.class.getName()));

				_included = true;
			}

		};

		ReflectionTestUtil.setFieldValue(
			sharedAssetsPortlet, "_viewSharedAssetsDisplayContextFactory",
			viewSharedAssetsDisplayContextFactory);

		sharedAssetsPortlet.doView(
			mockLiferayPortletRenderRequest, mockLiferayPortletRenderResponse);

		Assert.assertTrue(_included);
	}

	private boolean _included;

}