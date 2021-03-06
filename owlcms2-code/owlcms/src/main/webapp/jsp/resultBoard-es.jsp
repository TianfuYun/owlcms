<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8" isELIgnored="false" import="org.concordiainternational.competition.ui.generators.*,org.concordiainternational.competition.ui.*,org.concordiainternational.competition.data.*"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<html><!--
/*
 * Copyright 2009-2012, Jean-François Lamy
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
 --><head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<%  if (request.getParameter("time") == null) {
    // called directly, not from frame.  force automatic refresh.
    long current = System.currentTimeMillis();
    long expires = current + 4000;
    response.addDateHeader("Expires", expires);
    response.addDateHeader("Last-Modified", current);
    response.setHeader("Cache-Control","public");
%>
<meta http-equiv="refresh" content="5">
<%  };
	String platform = request.getParameter("platformName");
	if (platform == null) {
		out.println("Platform parameter expected. URL must include ?platformName=X");
		return;
	}
	pageContext.setAttribute("platform", platform);

	String style = request.getParameter("style");
	if (style == null) {
		out.println("Style parameter expected. URL must include ?style=X");
		return;
	}
	pageContext.setAttribute("style", style);

	ServletContext sCtx = this.getServletContext();
	SessionData groupData = (SessionData)sCtx.getAttribute(SessionData.MASTER_KEY+platform);
	if (groupData == null) return;

	java.util.List<Lifter> lifters = groupData.getCurrentDisplayOrder();
	if (lifters == null || lifters.size() == 0) {
		out.println("</head><body></body></html>");
		out.flush();
		return;
	}
	pageContext.setAttribute("lifters", lifters);
	pageContext.setAttribute("isMasters", Competition.isMasters());

	CompetitionSession group = groupData.getCurrentSession();
	if (group == null) {
		pageContext.removeAttribute("groupName");
		pageContext.setAttribute("useGroupName", false);
	} else {
		String groupName = group.getName();
		pageContext.setAttribute("groupName", groupName);
		pageContext.setAttribute("useGroupName", true);
		pageContext.setAttribute("liftsDone",
				TryFormatter.htmlFormatLiftsDone(groupData.getLiftsDone(),java.util.Locale.forLanguageTag("es-ES"))
				);
	}
%>
<title>Resultados</title>
<link rel="stylesheet" type="text/css" href="${style}" />
<!--  style type="text/css">
.requestedWeight {
	color: navy;
	font-size: medium;
	font-style: italic;
	font-weight: 400;
	text-align: center;
	width: 7%;
}
</style  -->
</head>
<body>
<div class="title">
<c:choose>
	<c:when test='${useGroupName}'>
		<span class="title">Grupo ${groupName} Resultados</span>
		<span class="liftsDone">${liftsDone}</span>
	</c:when>
	<c:otherwise>
		<span class="title">Resultados</span>
		<span class="liftsDone">${liftsDone}</span>
	</c:otherwise>
</c:choose>
</div>

<table>
	<thead>
		<tr>
			<th class="narrow" style='text-align: center'>Comenzar</th>
			<th>Nombre</th>
			<c:choose>
				<c:when test="${isMasters}">
					<th><nobr>Edad</nobr></th>
				</c:when>
			</c:choose>
			<th class="cat">Cat.</th>
			<th class='weight'>Peso</th>
			<th class='club'>Equipo</th>
			<th colspan="3">Arrancada</th>
			<th colspan="3">Dos Tiempos</th>
			<th>Total</th>
			<th class="cat" style='text-align: center'>Posición</th>
		</tr>
	</thead>
    <tbody>
           <%
            String prevCat = null;
           %>
        <c:forEach var="lifter" items="${lifters}">
            <jsp:useBean id="lifter" type="org.concordiainternational.competition.data.Lifter" />
            <%
                Lifter curLifter = (Lifter) pageContext.getAttribute("lifter");
                String curCat = curLifter.getLongCategory();
                pageContext.setAttribute("newCategory", !curCat.equals(prevCat));
                prevCat = curCat;
            %>
             <c:choose>
                 <c:when test="${newCategory}">
                 <tr><td colspan="0" style="height:0.1ex; border:none"></td></tr>
                 </c:when>
             </c:choose>
            <tr>
				<td class='narrow' style='text-align: right'>${lifter.startNumber}&nbsp;</td>
				<c:choose>
					<c:when test="${lifter.currentLifter}">
						<td class='name current'><nobr><%= lifter.getLastName().toUpperCase() %>, ${lifter.firstName}</nobr></td>
					</c:when>
					<c:otherwise>
						<td class='name'><nobr><%= lifter.getLastName().toUpperCase() %>, ${lifter.firstName}</nobr></td>
					</c:otherwise>
				</c:choose>
				<c:choose>
					<c:when test="${isMasters}">
						<td class='club'><nobr>${lifter.mastersAgeGroup}</nobr></td>
					</c:when>
				</c:choose>
				<td class="cat" ><nobr>${lifter.shortCategory}</nobr></td>
				<td class='narrow'><%= WeightFormatter.formatBodyWeight(lifter.getBodyWeight()) %></td>
				<td class='club'><nobr>${lifter.club}</nobr></td>
				<!--  td class="weight">&nbsp;</td>  -->
				<c:choose>
					<c:when test="${lifter.snatchAttemptsDone == 0}">
						<c:choose>
							<c:when test="${lifter.currentLifter}">
								<td class='currentWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:when>
							<c:otherwise>
								<td class='requestedWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:otherwise>
						</c:choose>
					</c:when>
					<c:when test="${lifter.snatchAttemptsDone > 0 }">
						<%= WeightFormatter.htmlFormatWeight(lifter.getSnatch1ActualLift()) %>
					</c:when>
					<c:otherwise>
						<td class='weight'></td>
					</c:otherwise>
				</c:choose>
				<c:choose>
					<c:when test="${lifter.snatchAttemptsDone == 1}">
						<c:choose>
							<c:when test="${lifter.currentLifter}">
								<td class='currentWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:when>
							<c:otherwise>
								<td class='requestedWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:otherwise>
						</c:choose>
					</c:when>
					<c:when test="${lifter.snatchAttemptsDone > 1}">
						<%= WeightFormatter.htmlFormatWeight(lifter.getSnatch2ActualLift()) %>
					</c:when>
					<c:otherwise>
						<td class='weight'></td>
					</c:otherwise>
				</c:choose>
				<c:choose>
					<c:when test="${lifter.snatchAttemptsDone == 2}">
						<c:choose>
							<c:when test="${lifter.currentLifter}">
								<td class='currentWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:when>
							<c:otherwise>
								<td class='requestedWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:otherwise>
						</c:choose>
					</c:when>
					<c:when test="${lifter.snatchAttemptsDone > 2}">
						<%= WeightFormatter.htmlFormatWeight(lifter.getSnatch3ActualLift()) %>
					</c:when>
					<c:otherwise>
						<td class='weight'></td>
					</c:otherwise>
				</c:choose>
				<c:choose>
					<c:when test="${lifter.attemptsDone == 3}">
						<c:choose>
							<c:when test="${lifter.currentLifter}">
								<td class='currentWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:when>
							<c:otherwise>
								<td class='requestedWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:otherwise>
						</c:choose>
					</c:when>
					<c:when test="${lifter.cleanJerkAttemptsDone > 0}">
						<%= WeightFormatter.htmlFormatWeight(lifter.getCleanJerk1ActualLift()) %>
					</c:when>
					<c:otherwise>
						<td class='requestedWeight'><%= lifter.getRequestedWeightForAttempt(4) %></td>
					</c:otherwise>
				</c:choose>
				<c:choose>
					<c:when test="${lifter.cleanJerkAttemptsDone == 1}">
						<c:choose>
							<c:when test="${lifter.currentLifter}">
								<td class='currentWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:when>
							<c:otherwise>
								<td class='requestedWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:otherwise>
						</c:choose>
					</c:when>
					<c:when test="${lifter.cleanJerkAttemptsDone > 1}">
						<%= WeightFormatter.htmlFormatWeight(lifter.getCleanJerk2ActualLift()) %>
					</c:when>
					<c:otherwise>
						<td class='weight'></td>
					</c:otherwise>
				</c:choose>
				<c:choose>
					<c:when test="${lifter.cleanJerkAttemptsDone == 2}">
						<c:choose>
							<c:when test="${lifter.currentLifter}">
								<td class='currentWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:when>
							<c:otherwise>
								<td class='requestedWeight'>${lifter.nextAttemptRequestedWeight}</td>
							</c:otherwise>
						</c:choose>
					</c:when>
					<c:when test="${lifter.cleanJerkAttemptsDone > 2}">
						<%= WeightFormatter.htmlFormatWeight(lifter.getCleanJerk3ActualLift()) %>
					</c:when>
					<c:otherwise>
						<td class='weight'></td>
					</c:otherwise>
				</c:choose>
				<c:choose>
					<c:when test="${lifter.total > 0}">
						<td class='weight'>${lifter.total}</td>
					</c:when>
					<c:otherwise>
						<td class='weight'>&ndash;</td>
					</c:otherwise>
				</c:choose>
				<c:choose>
					<c:when test="${lifter.rank > 0}">
						<td class='cat'>${lifter.rank}</td>
					</c:when>
					<c:otherwise>
						<td class='cat'>&ndash;</td>
					</c:otherwise>
				</c:choose>
			</tr>
		</c:forEach>
	</tbody>
</table>
</body>
</html>
