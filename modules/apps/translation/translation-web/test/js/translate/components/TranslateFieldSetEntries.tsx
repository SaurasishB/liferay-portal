/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import '@testing-library/jest-dom';
import {render, screen} from '@testing-library/react';
import React from 'react';

import TranslateFieldSetEntries from '../../../../src/main/resources/META-INF/resources/js/translate/components/TranslateFieldSetEntries';

jest.mock('frontend-editor-ckeditor-web', () => {
	const React = require('react');

	return {
		CKEditor5ClassicEditor: ({data, onReady}: any) => {
			const wrapperRef = React.useRef(null);

			React.useEffect(() => {
				const wrapper = wrapperRef.current;

				const textarea = wrapper.querySelector('textarea');

				textarea.value = data;
				wrapper.dataset.value = data;

				// Mirror the SourceEditing plugin: the cached value the editor
				// restores on exiting source mode is only updated by the
				// textarea's input event, never by a direct value assignment.

				textarea.addEventListener('input', () => {
					wrapper.dataset.value = textarea.value;
				});

				const sourceEditingPlugin = {
					_replacedRoots: new Map([['main', wrapper]]),
					isSourceEditingMode: true,
					on: () => {},
				};

				onReady({
					editing: {
						view: {
							domRoots: new Map([['main', wrapper]]),
						},
					},
					getData: () => wrapper.dataset.value,
					plugins: {
						get: () => sourceEditingPlugin,
					},
				});

				// eslint-disable-next-line react-hooks/exhaustive-deps
			}, []);

			return (
				<div ref={wrapperRef}>
					<textarea aria-label="source" defaultValue={data} />
				</div>
			);
		},
		ClassicEditor: () => null,
	};
});

const ID = 'infoField--description--0';
const ORIGINAL_TARGET = '<p>contenido original</p>';
const TRANSLATED_TARGET = '<p>contenido traducido</p>';

const infoFieldSetEntries = [
	{
		fields: [
			{
				editorConfiguration: {editorConfig: {}},
				html: true,
				id: 'infoField--description--',
				label: 'Description',
				multiline: false,
				sourceContent: ['<p>original content</p>'],
				sourceContentDir: 'ltr',
				targetContentDir: 'ltr',
				targetLanguageId: 'es_ES',
			},
		],
		legend: 'Basic Information',
	},
];

const renderComponent = (content: string) =>
	render(
		<TranslateFieldSetEntries
			autoTranslateEnabled={false}
			fetchAutoTranslateField={() => {}}
			infoFieldSetEntries={infoFieldSetEntries}
			onChange={() => {}}
			portletNamespace="_mock_TranslationPortlet_"
			targetFieldsContent={{
				[ID]: {content, message: '', status: ''},
			}}
		/>
	);

describe('TranslateFieldSetEntries', () => {
	beforeAll(() => {
		Liferay.FeatureFlags['LPD-11235'] = false;
	});

	afterAll(() => {
		delete Liferay.FeatureFlags['LPD-11235'];
	});

	it('refreshes the source editing textarea when a translation arrives while in source mode', () => {
		const {rerender} = renderComponent(ORIGINAL_TARGET);

		const textarea = screen.getByLabelText('source');

		expect(textarea).toHaveValue(ORIGINAL_TARGET);

		rerender(
			<TranslateFieldSetEntries
				autoTranslateEnabled={false}
				fetchAutoTranslateField={() => {}}
				infoFieldSetEntries={infoFieldSetEntries}
				onChange={() => {}}
				portletNamespace="_mock_TranslationPortlet_"
				targetFieldsContent={{
					[ID]: {
						content: TRANSLATED_TARGET,
						message: '',
						status: '',
					},
				}}
			/>
		);

		expect(textarea).toHaveValue(TRANSLATED_TARGET);

		// The value the editor restores when leaving source mode must also
		// reflect the translation, not the original content.

		expect(textarea.closest('div')).toHaveAttribute(
			'data-value',
			TRANSLATED_TARGET
		);
	});
});
