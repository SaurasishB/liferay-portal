/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {act, render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {openModal} from 'frontend-js-components-web';
import React from 'react';

import '@testing-library/jest-dom';

import PreviewButton from '../../../src/main/resources/META-INF/resources/js/article/PreviewButton';

jest.mock('frontend-js-components-web', () => ({
	openModal: jest.fn(),
	openToast: jest.fn(),
}));

const NAMESPACE = 'namespace';

const renderComponent = async ({hasFormChanges, markFormSaved}) => {
	const form = document.createElement('form');

	form.id = `${NAMESPACE}fm1`;
	form.method = 'post';
	form.innerHTML = `
		<input id="${NAMESPACE}articleId" name="${NAMESPACE}articleId" value="123" />
		<input id="${NAMESPACE}formDate" name="${NAMESPACE}formDate" />
		<input id="${NAMESPACE}version" name="${NAMESPACE}version" />
	`;

	document.body.appendChild(form);

	render(
		<PreviewButton
			disabled={false}
			getPreviewURL={() => 'http://localhost/preview'}
			hasFormChanges={hasFormChanges}
			markFormSaved={markFormSaved}
			namespace={NAMESPACE}
			newArticle={false}
			saveAsDraftURL="http://localhost/save-as-draft"
		/>,
		{container: form.appendChild(document.createElement('div'))}
	);

	await act(() => Promise.resolve());
};

const getCmd = () => {
	const [[, {body}]] = fetch.mock.calls;

	return body.get(`${NAMESPACE}cmd`);
};

describe('PreviewButton', () => {
	const {fetch: initialUtilFetch} = Liferay.Util;

	beforeEach(() => {
		Liferay.componentReady = jest.fn(() =>
			Promise.resolve({
				isLocked: () => false,
				lock: () => {},
				unlock: () => {},
			})
		);
		Liferay.Util.fetch = global.fetch;

		fetch.mockResponseOnce(
			JSON.stringify({
				articleId: '123',
				classPK: 1,
				friendlyUrlMap: {},
				version: 1.1,
			})
		);
	});

	afterEach(() => {
		document.getElementById(`${NAMESPACE}fm1`).remove();

		delete Liferay.componentReady;

		Liferay.Util.fetch = initialUtilFetch;

		jest.clearAllMocks();
	});

	it('previews the saved version when the form has not changed', async () => {
		await renderComponent({
			hasFormChanges: () => false,
			markFormSaved: jest.fn(),
		});

		await userEvent.click(screen.getByRole('button', {name: /preview/}));

		await waitFor(() => expect(openModal).toHaveBeenCalled());

		expect(getCmd()).toBe('preview');
	});

	it('saves a draft before previewing when the form has changed', async () => {
		const markFormSaved = jest.fn();

		await renderComponent({hasFormChanges: () => true, markFormSaved});

		await userEvent.click(screen.getByRole('button', {name: /preview/}));

		await waitFor(() => expect(openModal).toHaveBeenCalled());

		expect(getCmd()).toBe('update');
		expect(markFormSaved).toHaveBeenCalled();
	});
});
