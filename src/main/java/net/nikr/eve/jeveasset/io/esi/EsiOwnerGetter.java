/*
 * Copyright 2009-2018 Contributors (see credits.txt)
 *
 * This file is part of jEveAssets.
 *
 * jEveAssets is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * jEveAssets is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jEveAssets; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package net.nikr.eve.jeveasset.io.esi;

import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import net.nikr.eve.jeveasset.data.api.accounts.EsiOwner;
import net.nikr.eve.jeveasset.gui.dialogs.update.UpdateTask;
import static net.nikr.eve.jeveasset.io.esi.AbstractEsiGetter.DATASOURCE;
import net.nikr.eve.jeveasset.io.shared.AccountAdder;
import net.troja.eve.esi.ApiException;
import net.troja.eve.esi.ApiResponse;
import net.troja.eve.esi.model.CharacterInfo;
import net.troja.eve.esi.model.CharacterResponse;
import net.troja.eve.esi.model.CharacterRolesResponse;
import net.troja.eve.esi.model.CharacterRolesResponse.RolesEnum;
import net.troja.eve.esi.model.CorporationResponse;
import net.troja.eve.esi.model.EsiVerifyResponse;


public class EsiOwnerGetter extends AbstractEsiGetter implements AccountAdder{

	private boolean wrongEntry = false;

	public EsiOwnerGetter(EsiOwner owner, boolean forceUpdate) {
		super(null, owner, forceUpdate, owner.getAccountNextUpdate(), TaskType.OWNER);
	}

	public EsiOwnerGetter(UpdateTask updateTask, EsiOwner owner) {
		super(updateTask, owner, owner.getCorporationName() == null, owner.getAccountNextUpdate(), TaskType.OWNER);
	}

	@Override
	protected void update() throws ApiException {
		EsiVerifyResponse esiVerifyResponse = update(DEFAULT_RETRIES, new EsiHandler<EsiVerifyResponse>() {
			@Override
			public ApiResponse<EsiVerifyResponse> get() throws ApiException {
				return getMetaApiAuth().getVerifyWithHttpInfo(null, null, DATASOURCE, null, null);
			}
		});
		CharacterInfo characterInfo = new CharacterInfo(esiVerifyResponse);
		Set<RolesEnum> roles = EnumSet.noneOf(RolesEnum.class);
		Integer characterID = characterInfo.getCharacterID();
		//CharacterID to CorporationID
		CharacterResponse character = getCharacterApiOpen().getCharactersCharacterId(characterID, DATASOURCE, null);
		Integer corporationID = character.getCorporationId();
		//CorporationID to CorporationName
		CorporationResponse corporation = getCorporationApiOpen().getCorporationsCorporationId(corporationID, DATASOURCE, null);
		String corporationName = corporation.getName();
		boolean isCorporation = EsiScopes.CORPORATION_ROLES.isInScope(characterInfo.getScopes());
		if (isCorporation) { //Corporation
			//Updated Character Roles
			CharacterRolesResponse characterRolesResponse = getCharacterApiAuth().getCharactersCharacterIdRoles(characterID, DATASOURCE, null, null);
			roles.addAll(characterRolesResponse.getRoles());
		}
		if (((!isCorporation && characterID != owner.getOwnerID())
				|| (isCorporation && corporationID != owner.getOwnerID()))
				&& owner.getOwnerID() != 0) {
			addError(null, "Wrong Entry", null);
			wrongEntry = true;
			return;
		}
		owner.setCharacterOwnerHash(characterInfo.getCharacterOwnerHash());
		owner.setScopes(characterInfo.getScopes());
		owner.setIntellectualProperty(characterInfo.getIntellectualProperty());
		owner.setTokenType(characterInfo.getTokenType());
		owner.setRoles(roles);
		owner.setCorporationName(corporationName);
		if (owner.isCorporation()) {
			owner.setOwnerID(corporationID);
			owner.setOwnerName(corporationName);
		} else {
			owner.setOwnerID(characterInfo.getCharacterID());
			owner.setOwnerName(characterInfo.getCharacterName());
		}
		if (isPrivilegesLimited()) {
			addError(null, "LIMITED ACCOUNT", "Limited account data access\r\n(Fix: Options > Accounts... > Edit)");
			setError(null);
		}
	}

	@Override
	protected void setNextUpdate(Date date) {
		owner.setAccountNextUpdate(date);
	}

	@Override
	protected boolean haveAccess() {
		return true; //Always update accounts
	}

	@Override
	public boolean isPrivilegesLimited() {
		return owner.isPrivilegesLimited();
	}

	@Override
	public boolean isPrivilegesInvalid() {
		return owner.isPrivilegesInvalid();
	}

	@Override
	public boolean isInvalid() {
		return false;
	}

	@Override
	public boolean isWrongEntry() {
		return wrongEntry;
	}
}
