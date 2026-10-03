"""Sends buyers an email when one of their packages changes status.

Triggered by the order-events SQS queue. Each message is one package's status change,
published by order-service with everything needed to write the email.
"""
import html
import json
import os
from decimal import Decimal

import boto3
from botocore.exceptions import ClientError

ses = boto3.client("sesv2")
SENDER = os.environ["SENDER_EMAIL"]
SITE_URL = os.environ.get("SITE_URL", "").rstrip("/")

SUBJECTS = {
    "PAID": "Your order is confirmed",
    "SHIPPED": "Your package is on its way",
    "DELIVERED": "Your package was delivered",
    "CANCELLED": "Your order was cancelled",
}


def handler(event, context):
    # Report failures one message at a time, so a single bad message
    # doesn't make SQS redeliver the whole batch
    failures = []
    for record in event.get("Records", []):
        try:
            process(json.loads(record["body"]))
        except Exception as exc:  # noqa: BLE001
            print(f"Failed to process message {record['messageId']}: {exc}")
            failures.append({"itemIdentifier": record["messageId"]})
    return {"batchItemFailures": failures}


def process(evt):
    status = evt.get("newStatus")
    to = evt.get("buyerEmail")
    if status not in SUBJECTS or not to:
        print(f"Skipping {status} event for package {evt.get('sellerOrderId')}: no email needed")
        return

    subject, text, body_html = build_email(evt)
    try:
        ses.send_email(
            FromEmailAddress=SENDER,
            Destination={"ToAddresses": [to]},
            Content={"Simple": {
                "Subject": {"Data": subject},
                "Body": {"Text": {"Data": text}, "Html": {"Data": body_html}},
            }},
        )
        print(f"Sent {status} email for package {evt.get('sellerOrderId')}")
    except ClientError as exc:
        if exc.response["Error"]["Code"] == "MessageRejected":
            # In the SES sandbox, unverified recipients are rejected. Retrying won't help.
            print(f"SES rejected the email for package {evt.get('sellerOrderId')}: {exc}")
            return
        raise  # anything else (throttling, outages) is retried by SQS


def money(value):
    return f"${Decimal(str(value)):.2f}" if value is not None else ""


def build_email(evt):
    status = evt["newStatus"]
    name = evt.get("buyerName") or "there"
    order_short = str(evt.get("orderId", ""))[:8]
    order_link = f"{SITE_URL}/order/{evt.get('orderId')}" if SITE_URL else ""
    items = evt.get("items") or []

    if status == "PAID":
        intro = "Thanks for your order! We've received your payment and the seller is preparing your package."
    elif status == "SHIPPED":
        intro = "Good news: your package has shipped."
    elif status == "DELIVERED":
        intro = "Your package has been delivered. We hope you enjoy it!"
    else:
        refund = evt.get("refundedAmount")
        intro = "Your order has been cancelled."
        if refund is not None:
            intro += f" {money(refund)} has been refunded to your original payment method; it can take 5-10 days to appear."

    # Plain-text version
    lines = [f"Hi {name},", "", intro, "", f"Order {order_short}:"]
    lines += [f"  {i['name']} x {i['quantity']} - {money(Decimal(str(i['unitPrice'])) * i['quantity'])}" for i in items]
    lines.append(f"  Package total: {money(evt.get('packageSubtotal'))}")
    if status == "SHIPPED" and evt.get("trackingNumber"):
        lines += ["", f"Carrier: {evt.get('carrier')}", f"Tracking number: {evt['trackingNumber']}"]
        if evt.get("trackingUrl"):
            lines.append(f"Track it: {evt['trackingUrl']}")
    if order_link:
        lines += ["", f"View your order: {order_link}"]
    text = "\n".join(lines)

    # HTML version (every value escaped)
    e = html.escape
    rows = "".join(
        f"<tr><td>{e(i['name'])} &times; {i['quantity']}</td>"
        f"<td style='text-align:right'>{money(Decimal(str(i['unitPrice'])) * i['quantity'])}</td></tr>"
        for i in items
    )
    tracking = ""
    if status == "SHIPPED" and evt.get("trackingNumber"):
        link = (f" &middot; <a href='{e(evt['trackingUrl'])}'>Track package</a>" if evt.get("trackingUrl") else "")
        tracking = f"<p><strong>{e(evt.get('carrier') or '')}</strong> tracking number: {e(evt['trackingNumber'])}{link}</p>"
    button = f"<p><a href='{e(order_link)}'>View your order</a></p>" if order_link else ""
    body_html = f"""<div style="font-family:sans-serif;max-width:560px">
<p>Hi {e(name)},</p>
<p>{e(intro)}</p>
<p style="color:#777">Order {e(order_short)}</p>
<table style="width:100%;border-collapse:collapse">{rows}
<tr><td><strong>Package total</strong></td><td style='text-align:right'><strong>{money(evt.get('packageSubtotal'))}</strong></td></tr></table>
{tracking}{button}
<p style="color:#999;font-size:12px">OrderFlow (test mode, no real charges)</p>
</div>"""

    return SUBJECTS[status], text, body_html
