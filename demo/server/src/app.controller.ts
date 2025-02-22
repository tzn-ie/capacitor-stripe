import { Body, Controller, Post } from '@nestjs/common';
import { AppService } from './app.service';
import { Stripe } from 'stripe';
import { CreatePaymentIntentDTO } from './payment-intent.dto';

@Controller()
export class AppController {
  private stripe: Stripe;
  constructor(private readonly appService: AppService) {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    this.stripe = require('stripe')('sk_test_BpbfN6DaB1VhEDVMlLg33IIL');
  }

  @Post('intent')
  async createIntent(
    @Body() createPaymentIntentDto: CreatePaymentIntentDTO,
  ): Promise<{
    paymentIntent: string;
    ephemeralKey: string;
    customer: string;
  }> {
    /**
     * https://stripe.com/docs/payments/accept-a-payment?platform=ios
     */
    const customerId = await (async () => {
      if (createPaymentIntentDto.customer_id)
        return createPaymentIntentDto.customer_id;
      const customer = await this.stripe.customers.create();
      return customer.id;
    })();
    const ephemeralKey = await this.stripe.ephemeralKeys.create(
      { customer: customerId },
      { apiVersion: '2020-08-27' },
    );
    const paymentIntent = await this.stripe.paymentIntents.create({
      amount: createPaymentIntentDto.amount || 1099,
      currency: createPaymentIntentDto.currency || 'usd',
      customer: customerId,
    });
    return {
      paymentIntent: paymentIntent.client_secret,
      ephemeralKey: ephemeralKey.secret,
      customer: customerId,
    };
  }

  @Post('intent/without-customer')
  async createIntentWithoutCustomer(
    @Body() createPaymentIntentDto: CreatePaymentIntentDTO,
  ): Promise<{
    paymentIntent: string;
  }> {
    /**
     * https://stripe.com/docs/payments/accept-a-payment?platform=ios
     */
    const paymentIntent = await this.stripe.paymentIntents.create({
      amount: createPaymentIntentDto.amount || 1099,
      currency: createPaymentIntentDto.currency || 'usd',
    });
    return {
      paymentIntent: paymentIntent.client_secret,
    };
  }
}
