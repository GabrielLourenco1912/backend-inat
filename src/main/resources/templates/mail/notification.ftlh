<#ftl output_format="HTML" auto_esc=true strip_whitespace=true>
<#import "layout.ftlh" as layout>
<@layout.email title=subject preheader=preheader heading=subject>
    <p style="margin:0 0 18px; color:#344b4d; font-size:16px; line-height:1.7;">
        Olá, <strong>${recipientName}</strong>!
    </p>
    <p style="margin:0 0 26px; color:#344b4d; font-size:16px; line-height:1.7; white-space:pre-line;">${message}</p>
    <#if actionUrl?? && actionUrl?has_content>
        <table role="presentation" cellspacing="0" cellpadding="0" border="0"
               style="border-collapse:separate; margin:0 0 4px;">
            <tr>
                <td bgcolor="#088285" style="background-color:#088285; border-radius:7px;">
                    <a href="${actionUrl}" target="_blank" rel="noopener noreferrer"
                       style="display:inline-block; padding:13px 22px; color:#ffffff; font-size:14px; font-weight:700; line-height:1.2; text-decoration:none;">
                        Acessar o Portal INAT
                    </a>
                </td>
            </tr>
        </table>
    </#if>
</@layout.email>
