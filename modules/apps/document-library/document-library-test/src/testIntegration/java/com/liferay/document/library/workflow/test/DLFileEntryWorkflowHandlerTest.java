/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.document.library.workflow.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFileEntryTypeConstants;
import com.liferay.document.library.kernel.model.DLFileVersion;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLAppServiceUtil;
import com.liferay.document.library.kernel.service.DLFileEntryLocalServiceUtil;
import com.liferay.document.library.test.util.BaseDLAppTestCase;
import com.liferay.layout.page.template.constants.LayoutPageTemplateEntryTypeConstants;
import com.liferay.layout.page.template.model.LayoutPageTemplateEntry;
import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalServiceUtil;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserGroupRoleLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.kernel.workflow.WorkflowInstance;
import com.liferay.portal.kernel.workflow.WorkflowInstanceManagerUtil;
import com.liferay.portal.kernel.workflow.WorkflowTask;
import com.liferay.portal.kernel.workflow.WorkflowTaskManagerUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;

import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.mock.web.MockHttpServletRequest;

/**
 * @author Saurasish Basak
 */
@RunWith(Arquillian.class)
public class DLFileEntryWorkflowHandlerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		_user = UserTestUtil.addGroupAdminUser(_group);

		Role role = _roleLocalService.getRole(
			_group.getCompanyId(), RoleConstants.SITE_ADMINISTRATOR);

		_userGroupRoleLocalService.addUserGroupRole(
			_user.getUserId(), _group.getGroupId(), role.getRoleId());

		_permissionChecker = PermissionThreadLocal.getPermissionChecker();

		PermissionThreadLocal.setPermissionChecker(
			_permissionCheckerFactory.create(_user));

		_originalName = PrincipalThreadLocal.getName();

		PrincipalThreadLocal.setName(_user.getUserId());
	}

	@After
	public void tearDown() throws Exception {
		PermissionThreadLocal.setPermissionChecker(_permissionChecker);
		PrincipalThreadLocal.setName(_originalName);
	}

	@Test
	public void testContributeWorkflowContext() throws Exception {
		Folder folder = DLAppServiceUtil.addFolder(
			null, _group.getGroupId(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			RandomTestUtil.randomString(), RandomTestUtil.randomString(),
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), _user.getUserId()));

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), _user.getUserId());

		_activateSingleApproverWorkflow(serviceContext, folder);

		_addLayoutPageTemplateEntry(StringUtil.randomString());

		FileEntry fileEntry = DLAppServiceUtil.addFileEntry(
			StringUtil.randomString(), _group.getGroupId(),
			folder.getFolderId(), StringUtil.randomString(),
			ContentTypes.TEXT_PLAIN, StringUtil.randomString(),
			StringPool.BLANK, StringPool.BLANK, StringPool.BLANK,
			BaseDLAppTestCase.CONTENT.getBytes(), null, null, null,
			serviceContext);

		DLFileEntry dlFileEntry = DLFileEntryLocalServiceUtil.getDLFileEntry(
			fileEntry.getFileEntryId());

		Assert.assertEquals(
			WorkflowConstants.STATUS_PENDING, dlFileEntry.getStatus());

		WorkflowInstance workflowInstance = _approveWorkflowTask(dlFileEntry);

		dlFileEntry = DLFileEntryLocalServiceUtil.getFileEntry(
			dlFileEntry.getFileEntryId());

		Assert.assertEquals(
			WorkflowConstants.STATUS_APPROVED, dlFileEntry.getStatus());

		Map<String, Serializable> approvedContext =
			workflowInstance.getWorkflowContext();

		Assert.assertNotEquals(
			StringPool.BLANK,
			approvedContext.get(WorkflowConstants.CONTEXT_URL));
	}

	private void _activateSingleApproverWorkflow(
			ServiceContext serviceContext, Folder folder)
		throws Exception {

		HttpServletRequest httpServletRequest = new MockHttpServletRequest();

		httpServletRequest.setAttribute(
			WebKeys.THEME_DISPLAY, _getThemeDisplay());

		serviceContext.setRequest(httpServletRequest);

		serviceContext.setAttribute(
			"restrictionType", DLFolderConstants.RESTRICTION_TYPE_WORKFLOW);
		serviceContext.setAttribute(
			"workflowDefinition" +
				DLFileEntryTypeConstants.FILE_ENTRY_TYPE_ID_ALL,
			"Single Approver@1");

		DLAppServiceUtil.updateFolder(
			folder.getFolderId(), folder.getName(), folder.getDescription(),
			serviceContext);
	}

	private void _addLayoutPageTemplateEntry(String name) throws Exception {
		LayoutPageTemplateEntry layoutPageTemplateEntry =
			LayoutPageTemplateEntryLocalServiceUtil.addLayoutPageTemplateEntry(
				null, _user.getUserId(), _group.getGroupId(), 0,
				name.toLowerCase(LocaleUtil.ROOT),
				PortalUtil.getClassNameId(FileEntry.class.getName()), 0, name,
				LayoutPageTemplateEntryTypeConstants.DISPLAY_PAGE, 0L, 0,
				ServiceContextTestUtil.getServiceContext(
					_group.getGroupId(), _user.getUserId()));

		LayoutPageTemplateEntryLocalServiceUtil.updateLayoutPageTemplateEntry(
			layoutPageTemplateEntry.getLayoutPageTemplateEntryId(), true);
	}

	private WorkflowInstance _approveWorkflowTask(DLFileEntry dlFileEntry)
		throws Exception {

		DLFileVersion latestFileVersion = dlFileEntry.getLatestFileVersion(
			true);

		List<WorkflowInstance> workflowInstances =
			WorkflowInstanceManagerUtil.getWorkflowInstances(
				_group.getCompanyId(), _user.getUserId(),
				DLFileEntry.class.getName(),
				latestFileVersion.getFileVersionId(), false, -1, -1, null);

		WorkflowInstance workflowInstance = workflowInstances.get(0);

		List<WorkflowTask> workflowTasks =
			WorkflowTaskManagerUtil.getWorkflowTasksByWorkflowInstance(
				_group.getCompanyId(), null,
				workflowInstance.getWorkflowInstanceId(), false, 0, 1, null);

		if (ListUtil.isNotEmpty(workflowTasks)) {
			WorkflowTask workflowTask = workflowTasks.get(0);

			WorkflowTaskManagerUtil.assignWorkflowTaskToUser(
				_group.getCompanyId(), _user.getUserId(),
				workflowTask.getWorkflowTaskId(), _user.getUserId(), null, null,
				workflowInstance.getWorkflowContext());

			WorkflowTaskManagerUtil.completeWorkflowTask(
				_group.getCompanyId(), _user.getUserId(),
				workflowTask.getWorkflowTaskId(), "approve", null,
				workflowInstance.getWorkflowContext());
		}

		return workflowInstance;
	}

	private ThemeDisplay _getThemeDisplay() throws Exception {
		ThemeDisplay themeDisplay = new ThemeDisplay();

		themeDisplay.setCompany(
			CompanyLocalServiceUtil.getCompany(_user.getCompanyId()));
		themeDisplay.setLanguageId(_user.getLanguageId());
		themeDisplay.setPermissionChecker(
			PermissionThreadLocal.getPermissionChecker());
		themeDisplay.setRequest(new MockHttpServletRequest());
		themeDisplay.setScopeGroupId(_group.getGroupId());
		themeDisplay.setServerName("localhost");
		themeDisplay.setServerPort(8080);
		themeDisplay.setSiteGroupId(_group.getGroupId());
		themeDisplay.setUser(TestPropsValues.getUser());

		return themeDisplay;
	}

	private static String _originalName;

	@DeleteAfterTestRun
	private Group _group;

	private PermissionChecker _permissionChecker;

	@Inject
	private PermissionCheckerFactory _permissionCheckerFactory;

	@Inject
	private RoleLocalService _roleLocalService;

	private User _user;

	@Inject
	private UserGroupRoleLocalService _userGroupRoleLocalService;

}