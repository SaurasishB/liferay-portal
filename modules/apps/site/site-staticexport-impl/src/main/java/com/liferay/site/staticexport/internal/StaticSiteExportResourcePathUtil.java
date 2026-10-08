/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.site.staticexport.internal;

import com.liferay.petra.string.CharPool;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

/**
 * @author Víctor Galán
 */
public class StaticSiteExportResourcePathUtil {

	public static String getPath(String url) {
		String path = url;
		String queryString = null;

		int index = url.indexOf(CharPool.QUESTION);

		if (index != -1) {
			path = url.substring(0, index);
			queryString = url.substring(index + 1);
		}

		path = _addExtension(
			StringUtil.removeFirst(path, StringPool.SLASH), queryString);

		if (Validator.isNull(queryString)) {
			return path;
		}

		String digest = StringUtil.toHexString(queryString.hashCode());

		int extensionIndex = path.lastIndexOf(CharPool.PERIOD);

		if (extensionIndex <= path.lastIndexOf(CharPool.SLASH)) {
			return path + StringPool.PERIOD + digest;
		}

		return StringBundler.concat(
			path.substring(0, extensionIndex), StringPool.PERIOD, digest,
			path.substring(extensionIndex));
	}

	private static String _addExtension(String path, String queryString) {
		int slashIndex = path.lastIndexOf(CharPool.SLASH);

		if (path.indexOf(CharPool.PERIOD, slashIndex + 1) != -1) {
			return path;
		}

		String extension = _getPathExtension(path, slashIndex);

		if (extension == null) {
			extension = _getQueryStringExtension(queryString);
		}

		if (extension == null) {
			return path;
		}

		return path + StringPool.PERIOD + extension;
	}

	private static String _getPathExtension(String path, int slashIndex) {
		int end = slashIndex;

		while (end > 0) {
			int begin = path.lastIndexOf(CharPool.SLASH, end - 1);

			String segment = path.substring(begin + 1, end);

			int periodIndex = segment.lastIndexOf(CharPool.PERIOD);

			if (periodIndex != -1) {
				String extension = segment.substring(periodIndex + 1);

				if (!_isExtension(extension)) {
					return null;
				}

				return extension;
			}

			end = begin;
		}

		return null;
	}

	private static String _getQueryStringExtension(String queryString) {
		if (Validator.isNull(queryString)) {
			return null;
		}

		String extension = null;

		for (String parameter :
				StringUtil.split(queryString, CharPool.AMPERSAND)) {

			if (!parameter.startsWith(StringPool.SLASH)) {
				continue;
			}

			int index = parameter.lastIndexOf(CharPool.PERIOD);

			if (index != -1) {
				extension = parameter.substring(index + 1);
			}
		}

		if (!_isExtension(extension)) {
			return null;
		}

		return extension;
	}

	private static boolean _isExtension(String extension) {
		if (Validator.isNull(extension) ||
			(extension.length() > _EXTENSION_MAX_LENGTH)) {

			return false;
		}

		for (char c : extension.toCharArray()) {
			if (!Character.isLetterOrDigit(c)) {
				return false;
			}
		}

		return true;
	}

	private static final int _EXTENSION_MAX_LENGTH = 5;

}