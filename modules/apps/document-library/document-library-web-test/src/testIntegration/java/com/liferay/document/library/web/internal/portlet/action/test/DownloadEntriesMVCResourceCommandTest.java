/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.document.library.web.internal.portlet.action.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.configuration.test.util.ConfigurationTemporarySwapper;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCResourceCommand;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.test.portlet.MockLiferayResourceRequest;
import com.liferay.portal.kernel.test.portlet.MockLiferayResourceResponse;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import jakarta.portlet.ResourceResponse;

import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Saurasish Basak
 */
@RunWith(Arquillian.class)
public class DownloadEntriesMVCResourceCommandTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();
	}

	@Test
	public void testServeResourceDownloadEntries() throws Exception {
		FileEntry fileEntry = _addFileEntry(
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, "notes.txt", "notes");

		Folder folder = _addFolder(
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, "Archive");

		_addFileEntry(folder.getFolderId(), "old.txt", "old");

		MockLiferayResourceRequest mockLiferayResourceRequest =
			_getMockLiferayResourceRequest(
				"/document_library/download_entry",
				DLFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		mockLiferayResourceRequest.setParameter(
			"rowIdsFileEntry", String.valueOf(fileEntry.getFileEntryId()));
		mockLiferayResourceRequest.setParameter(
			"rowIdsFolder", String.valueOf(folder.getFolderId()));

		Map<String, String> zipEntries = _serveResource(
			mockLiferayResourceRequest);

		Assert.assertEquals(zipEntries.toString(), 2, zipEntries.size());
		Assert.assertEquals("old", zipEntries.get("Archive/old.txt"));
		Assert.assertEquals("notes", zipEntries.get("notes.txt"));
	}

	@Test
	public void testServeResourceDownloadFolder() throws Exception {
		Folder folder = _addFolder(
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, "Reports");

		// Nested folder

		Folder subfolder = _addFolder(folder.getFolderId(), "2025");

		_addFileEntry(subfolder.getFolderId(), "q1.txt", "q1");

		// File shortcut whose file name collides with a sibling file entry

		_addFileEntry(folder.getFolderId(), "report.txt", "original");

		Folder otherFolder = _addFolder(
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, "Other");

		FileEntry fileEntry = _addFileEntry(
			otherFolder.getFolderId(), "report.txt", "shortcut");

		_dlAppLocalService.addFileShortcut(
			null, TestPropsValues.getUserId(), _group.getGroupId(),
			folder.getFolderId(), fileEntry.getFileEntryId(),
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		// More entries than a single batch

		for (int i = 0; i < (_BATCH_SIZE + 1); i++) {
			_addFileEntry(folder.getFolderId(), i + ".txt", String.valueOf(i));
		}

		Map<String, String> zipEntries = _serveResource(
			_getMockLiferayResourceRequest(
				"/document_library/download_folder", folder.getFolderId()));

		Assert.assertEquals(
			zipEntries.toString(), _BATCH_SIZE + 4, zipEntries.size());
		Assert.assertEquals("q1", zipEntries.get("2025/q1.txt"));
		Assert.assertEquals(
			String.valueOf(_BATCH_SIZE), zipEntries.get(_BATCH_SIZE + ".txt"));
		Assert.assertTrue(
			zipEntries.toString(), zipEntries.containsKey("report.txt"));
		Assert.assertTrue(
			zipEntries.toString(), zipEntries.containsKey("report_1.txt"));
	}

	@Test
	public void testServeResourceDownloadFolderWhenSizeExceedsLimit()
		throws Exception {

		Folder folder = _addFolder(
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, "Reports");

		_addFileEntry(folder.getFolderId(), "q1.txt", "q1");

		try (ConfigurationTemporarySwapper configurationTemporarySwapper =
				new ConfigurationTemporarySwapper(
					"com.liferay.document.library.internal.configuration." +
						"DLSizeLimitConfiguration",
					HashMapDictionaryBuilder.<String, Object>put(
						"maxSizeToDownload", 1L
					).build())) {

			TestMockLiferayResourceResponse testMockLiferayResourceResponse =
				new TestMockLiferayResourceResponse();

			_mvcResourceCommand.serveResource(
				_getMockLiferayResourceRequest(
					"/document_library/download_folder", folder.getFolderId()),
				testMockLiferayResourceResponse);

			Assert.assertEquals(
				String.valueOf(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE),
				testMockLiferayResourceResponse.getProperty(
					ResourceResponse.HTTP_STATUS_CODE));

			ByteArrayOutputStream byteArrayOutputStream =
				(ByteArrayOutputStream)
					testMockLiferayResourceResponse.getPortletOutputStream();

			Assert.assertEquals(
				0,
				_getZipEntries(
					byteArrayOutputStream.toByteArray()
				).size());
		}
	}

	private FileEntry _addFileEntry(
			long folderId, String fileName, String content)
		throws Exception {

		return _dlAppLocalService.addFileEntry(
			null, TestPropsValues.getUserId(), _group.getGroupId(), folderId,
			fileName, ContentTypes.TEXT_PLAIN, content.getBytes(), null, null,
			null,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));
	}

	private Folder _addFolder(long parentFolderId, String name)
		throws Exception {

		return _dlAppLocalService.addFolder(
			null, TestPropsValues.getUserId(), _group.getGroupId(),
			parentFolderId, name, StringPool.BLANK,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));
	}

	private MockLiferayResourceRequest _getMockLiferayResourceRequest(
			String resourceID, long folderId)
		throws Exception {

		MockLiferayResourceRequest mockLiferayResourceRequest =
			new MockLiferayResourceRequest();

		ThemeDisplay themeDisplay = new ThemeDisplay();

		themeDisplay.setCompany(
			_companyLocalService.getCompany(TestPropsValues.getCompanyId()));
		themeDisplay.setLocale(LocaleUtil.US);
		themeDisplay.setPermissionChecker(
			PermissionThreadLocal.getPermissionChecker());
		themeDisplay.setScopeGroupId(_group.getGroupId());
		themeDisplay.setSiteGroupId(_group.getGroupId());
		themeDisplay.setUser(TestPropsValues.getUser());

		mockLiferayResourceRequest.setAttribute(
			WebKeys.THEME_DISPLAY, themeDisplay);

		mockLiferayResourceRequest.setParameter(
			"folderId", String.valueOf(folderId));
		mockLiferayResourceRequest.setParameter(
			"repositoryId", String.valueOf(_group.getGroupId()));
		mockLiferayResourceRequest.setResourceID(resourceID);

		return mockLiferayResourceRequest;
	}

	private Map<String, String> _getZipEntries(byte[] bytes) throws Exception {
		Map<String, String> zipEntries = new LinkedHashMap<>();

		try (ZipInputStream zipInputStream = new ZipInputStream(
				new ByteArrayInputStream(bytes))) {

			ZipEntry zipEntry = zipInputStream.getNextEntry();

			while (zipEntry != null) {
				zipEntries.put(
					zipEntry.getName(),
					new String(zipInputStream.readAllBytes()));

				zipEntry = zipInputStream.getNextEntry();
			}
		}

		return zipEntries;
	}

	private Map<String, String> _serveResource(
			MockLiferayResourceRequest mockLiferayResourceRequest)
		throws Exception {

		MockLiferayResourceResponse mockLiferayResourceResponse =
			new MockLiferayResourceResponse();

		_mvcResourceCommand.serveResource(
			mockLiferayResourceRequest, mockLiferayResourceResponse);

		ByteArrayOutputStream byteArrayOutputStream =
			(ByteArrayOutputStream)
				mockLiferayResourceResponse.getPortletOutputStream();

		return _getZipEntries(byteArrayOutputStream.toByteArray());
	}

	private static final int _BATCH_SIZE = 100;

	@Inject
	private CompanyLocalService _companyLocalService;

	@Inject
	private DLAppLocalService _dlAppLocalService;

	@DeleteAfterTestRun
	private Group _group;

	@Inject(filter = "mvc.command.name=/document_library/download_folder")
	private MVCResourceCommand _mvcResourceCommand;

	private static class TestMockLiferayResourceResponse
		extends MockLiferayResourceResponse {

		@Override
		public String getProperty(String name) {
			return _properties.get(name);
		}

		@Override
		public void setProperty(String name, String value) {
			_properties.put(name, value);
		}

		private final Map<String, String> _properties = new HashMap<>();

	}

}